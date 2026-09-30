import { fileURLToPath, URL } from "node:url";
import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";

const envDir = fileURLToPath(new URL("..", import.meta.url));

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, envDir, "");
  const apiProxyTarget = env.VITE_API_PROXY_TARGET || `http://localhost:${env.SERVER_PORT || "8080"}`;

  return {
    envDir,
    plugins: [vue()],
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", import.meta.url)),
      },
    },
    server: {
      proxy: {
        "/api": {
          target: apiProxyTarget,
          changeOrigin: true,
          configure(proxy) {
            proxy.on("error", (_error, _request, response) => {
              if (!response.writeHead || response.headersSent || response.writableEnded) return;
              response.writeHead(503, { "Content-Type": "application/json; charset=utf-8" });
              response.end(JSON.stringify({
                success: false,
                message: "旅行服务暂时无法连接，请稍后重试。",
              }));
            });
          },
        },
      },
    },
  };
});
