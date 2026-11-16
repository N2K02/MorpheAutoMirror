package dev.n2k02.morpheautomirror;

/** Explicitly authorized Shizuku UserService. No generic arbitrary shell commands. */
interface IScrcpyShellService {
    int getProcessUid() = 1;
    void stageServer(in byte[] serverJar) = 2;
    void startServer(String scid) = 3;
    void stopServer() = 4;
    String getLastError() = 5;
    void destroy() = 16777114;
}
