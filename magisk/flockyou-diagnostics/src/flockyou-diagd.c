#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stddef.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/un.h>
#include <unistd.h>

#define SOCKET_NAME "flockyou_diag"
#define SHANNON_PATH "/dev/umts_dm0"
static const char *CCCI_PATHS[] = {"/dev/ccci_raw_dhl", "/dev/ccci_ccb_dhl", NULL};
#define PACKAGES_LIST "/data/system/packages.list"
#define MAX_PACKAGE 256
#define COPY_BUFFER 8192

enum { OP_PING = 0x01, OP_SHANNON_STREAM = 0x02, OP_CCCI_STREAM = 0x03 };
enum { ST_OK = 0, ST_UNAUTHORIZED = 1, ST_BAD_REQUEST = 2, ST_UNAVAILABLE = 3, ST_IO = 4 };

static int write_all(int fd, const void *data, size_t len) {
    const uint8_t *p = (const uint8_t *)data;
    while (len) {
        ssize_t n = write(fd, p, len);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) return -1;
        p += (size_t)n; len -= (size_t)n;
    }
    return 0;
}

static uint8_t capability_bits(void) {
    uint8_t bits = 0;
    if (access(SHANNON_PATH, R_OK) == 0) bits |= 0x01;
    for (size_t i = 0; CCCI_PATHS[i]; ++i) {
        if (access(CCCI_PATHS[i], R_OK) == 0) { bits |= 0x02; break; }
    }
    return bits;
}

static int send_status(int fd, uint8_t status) {
    uint8_t header[8] = {'F','Y','D','1', status, capability_bits(), 0, 0};
    return write_all(fd, header, sizeof(header));
}

static bool package_allowed_file(const char *allow_file, const char *package) {
    FILE *f = fopen(allow_file, "re");
    if (!f) return false;
    char line[MAX_PACKAGE + 8];
    bool allowed = false;
    while (fgets(line, sizeof(line), f)) {
        char *p = line;
        while (*p == ' ' || *p == '\t') ++p;
        if (*p == '#' || *p == '\n' || *p == '\0') continue;
        p[strcspn(p, "\r\n \t")] = '\0';
        if (strcmp(p, package) == 0) { allowed = true; break; }
    }
    fclose(f);
    return allowed;
}

static bool package_for_uid(const char *allow_file, uid_t uid, char out[MAX_PACKAGE]) {
    FILE *f = fopen(PACKAGES_LIST, "re");
    if (!f) return false;
    char line[1024], package[MAX_PACKAGE];
    unsigned parsed_uid = 0;
    bool found = false;
    while (fgets(line, sizeof(line), f)) {
        package[0] = '\0'; parsed_uid = 0;
        if (sscanf(line, "%255s %u", package, &parsed_uid) != 2) continue;
        if ((uid_t)parsed_uid != uid) continue;
        if (!package_allowed_file(allow_file, package)) continue;
        snprintf(out, MAX_PACKAGE, "%s", package);
        found = true;
        break;
    }
    fclose(f);
    return found;
}

static bool process_matches_package(pid_t pid, const char *package) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);
    FILE *f = fopen(path, "re");
    if (!f) return false;
    char cmdline[MAX_PACKAGE + 64] = {0};
    size_t n = fread(cmdline, 1, sizeof(cmdline) - 1, f);
    fclose(f);
    if (n == 0) return false;
    size_t plen = strlen(package);
    return strcmp(cmdline, package) == 0 ||
        (strncmp(cmdline, package, plen) == 0 && cmdline[plen] == ':');
}

static bool authorized_peer(int fd, const char *allow_file, struct ucred *out_cred) {
    struct ucred cred;
    socklen_t len = sizeof(cred);
    if (getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &cred, &len) != 0) return false;
    char package[MAX_PACKAGE] = {0};
    if (!package_for_uid(allow_file, cred.uid, package)) return false;
    if (!process_matches_package(cred.pid, package)) return false;
    if (out_cred) *out_cred = cred;
    fprintf(stderr, "authorized uid=%u pid=%d package=%s\n",
        (unsigned)cred.uid, cred.pid, package);
    return true;
}

static int open_first_readable(const char *const *paths) {
    for (size_t i = 0; paths[i]; ++i) {
        int fd = open(paths[i], O_RDONLY | O_CLOEXEC | O_NONBLOCK);
        if (fd >= 0) return fd;
    }
    return -1;
}

static void stream_fd(int client, int diag) {
    if (send_status(client, ST_OK) != 0) { close(diag); return; }
    int flags = fcntl(diag, F_GETFL, 0);
    if (flags >= 0) fcntl(diag, F_SETFL, flags & ~O_NONBLOCK);
    uint8_t buffer[COPY_BUFFER];
    for (;;) {
        ssize_t n = read(diag, buffer, sizeof(buffer));
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) break;
        if (write_all(client, buffer, (size_t)n) != 0) break;
    }
    close(diag);
}

static void stream_path(int client, const char *path) {
    int fd = open(path, O_RDONLY | O_CLOEXEC | O_NONBLOCK);
    if (fd < 0) { send_status(client, ST_UNAVAILABLE); return; }
    stream_fd(client, fd);
}

static void stream_ccci(int client) {
    int fd = open_first_readable(CCCI_PATHS);
    if (fd < 0) { send_status(client, ST_UNAVAILABLE); return; }
    stream_fd(client, fd);
}

static void handle_client(int client, const char *allow_file) {
    if (!authorized_peer(client, allow_file, NULL)) {
        send_status(client, ST_UNAUTHORIZED);
        return;
    }
    uint8_t op = 0;
    ssize_t n;
    do { n = read(client, &op, 1); } while (n < 0 && errno == EINTR);
    if (n != 1) { send_status(client, ST_BAD_REQUEST); return; }
    switch (op) {
        case OP_PING:
            send_status(client, ST_OK);
            break;
        case OP_SHANNON_STREAM:
            stream_path(client, SHANNON_PATH);
            break;
        case OP_CCCI_STREAM:
            stream_ccci(client);
            break;
        default:
            send_status(client, ST_BAD_REQUEST);
            break;
    }
}

static int create_server(void) {
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return -1;
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0';
    memcpy(addr.sun_path + 1, SOCKET_NAME, strlen(SOCKET_NAME));
    socklen_t len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + strlen(SOCKET_NAME));
    if (bind(fd, (struct sockaddr *)&addr, len) != 0) {
        fprintf(stderr, "bind failed: %s\n", strerror(errno));
        close(fd); return -1;
    }
    if (listen(fd, 8) != 0) {
        fprintf(stderr, "listen failed: %s\n", strerror(errno));
        close(fd); return -1;
    }
    return fd;
}

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "usage: %s /path/to/allowed_packages\n", argv[0]);
        return 64;
    }
    signal(SIGPIPE, SIG_IGN);
    signal(SIGCHLD, SIG_IGN);
    int server = create_server();
    if (server < 0) return 1;
    fprintf(stderr, "flockyou-diagd ready on abstract @%s\n", SOCKET_NAME);
    for (;;) {
        int client = accept4(server, NULL, NULL, SOCK_CLOEXEC);
        if (client < 0) {
            if (errno == EINTR) continue;
            fprintf(stderr, "accept failed: %s\n", strerror(errno));
            break;
        }
        pid_t child = fork();
        if (child == 0) {
            close(server);
            handle_client(client, argv[1]);
            close(client);
            _exit(0);
        }
        if (child < 0) fprintf(stderr, "fork failed: %s\n", strerror(errno));
        close(client);
    }
    close(server);
    return 1;
}
