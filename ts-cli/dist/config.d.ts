export interface JwcodeConfig {
    backend_url: string;
    ws_url: string;
    ws_auth_token: string;
}
export declare function loadConfig(): JwcodeConfig;
