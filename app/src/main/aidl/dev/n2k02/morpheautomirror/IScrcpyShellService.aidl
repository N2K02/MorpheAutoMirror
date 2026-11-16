package dev.n2k02.morpheautomirror;

/** Explicitly authorized Shizuku UserService. No generic arbitrary shell commands. */
interface IScrcpyShellService {
    int getProcessUid();
    void stageServer(in byte[] serverJar);
    void startServer(String scid);
    void stopServer();
    String getLastError();
    void destroy() = 16777114;
}
