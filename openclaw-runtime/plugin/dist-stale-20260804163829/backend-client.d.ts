import type { VernTripPluginConfig } from "./config.js";
export interface WorkspaceScopeResponse {
    globalScope: boolean;
    totalScopeFileCount: number;
    documents: Array<{
        path: string;
        citeLabel: string;
        sectionTitle: string;
        chunks: string[];
    }>;
    unreadableFiles: string[];
}
export declare function readWorkspaceScope(config: VernTripPluginConfig, sessionKey: string): Promise<WorkspaceScopeResponse>;
//# sourceMappingURL=backend-client.d.ts.map