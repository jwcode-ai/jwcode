package com.jwcode.cli.commands;

import com.jwcode.core.service.AuthService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.Scanner;

/**
 * LoginCommand - /login 命令
 * 
 * 功能说明：
 * 用户登录认证，支持 API Key 和 OAuth 认证流程。
 * 
 * 认证方式：
 * - API Key: 直接输入 sk- 开头的 API Key
 * - OAuth: 通过浏览器完成 OAuth 授权流程
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/login", description = "用户登录", 
         aliases = {"/auth", "/signin"})
public class LoginCommand implements Runnable {
    
    private final AuthService authService;
    
    @Parameters(index = "0", description = "认证方式：apikey, oauth", 
                defaultValue = "apikey")
    private String authMethod;
    
    @Option(names = {"-k", "--key"}, description = "API Key（以 sk-开头）")
    private String apiKey;
    
    @Option(names = {"-o", "--open-browser"}, description = "自动打开浏览器进行 OAuth 认证")
    private boolean openBrowser;
    
    @Option(names = {"-s", "--show-status"}, description = "登录后显示状态信息")
    private boolean showStatus;
    
    public LoginCommand() {
        this.authService = new AuthService();
    }
    
    @Override
    public void run() {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║           JWCode 用户登录              ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
        
        switch (authMethod.toLowerCase()) {
            case "apikey":
            case "api-key":
            case "api_key":
                handleApiKeyLogin();
                break;
            case "oauth":
                handleOAuthLogin();
                break;
            default:
                System.out.println("未知的认证方式：" + authMethod);
                System.out.println("可用方式：apikey, oauth");
        }
        
        if (showStatus && authService.isAuthenticated()) {
            System.out.println();
            System.out.println("认证状态：已认证");
            System.out.println("认证类型：" + authService.getAuthType());
        }
    }
    
    /**
     * 处理 API Key 登录
     */
    private void handleApiKeyLogin() {
        String keyToUse = apiKey;
        
        // 如果没有提供 API Key，提示用户输入
        if (keyToUse == null || keyToUse.trim().isEmpty()) {
            System.out.println("请输入 API Key（以 sk-开头）:");
            System.out.print("> ");
            
            try (Scanner scanner = new Scanner(System.in)) {
                if (scanner.hasNextLine()) {
                    keyToUse = scanner.nextLine().trim();
                }
            }
        }
        
        // 验证 API Key 格式
        if (keyToUse == null || keyToUse.trim().isEmpty()) {
            System.out.println("❌ 未提供 API Key");
            System.out.println();
            System.out.println("使用方法:");
            System.out.println("  /login apikey -k sk-xxxxxxxxxxxxx");
            System.out.println("  或在提示后直接输入 API Key");
            return;
        }
        
        if (!keyToUse.startsWith("sk-")) {
            System.out.println("❌ API Key 格式错误：必须以 sk- 开头");
            System.out.println("请检查您的 API Key 并重试");
            return;
        }
        
        // 执行认证
        boolean success = authService.authenticateWithApiKey(keyToUse);
        
        if (success) {
            System.out.println("✓ 登录成功！");
            System.out.println();
            System.out.println("提示：");
            System.out.println("  - 使用 /status 查看当前状态");
            System.out.println("  - 使用 /logout 登出");
        } else {
            System.out.println("❌ 登录失败，请检查 API Key 是否正确");
        }
    }
    
    /**
     * 处理 OAuth 登录
     */
    private void handleOAuthLogin() {
        System.out.println("OAuth 登录流程：");
        System.out.println();
        System.out.println("1. 打开浏览器访问以下地址：");
        System.out.println("   https://auth.anthropic.com/oauth2/auth");
        System.out.println();
        System.out.println("2. 完成授权后，将授权码粘贴到下方：");
        System.out.println();
        
        // 如果设置了自动打开浏览器
        if (openBrowser) {
            System.out.println("正在尝试打开浏览器...");
            try {
                String url = "https://auth.anthropic.com/oauth2/auth";
                // Windows
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
                } else {
                    // Linux/Mac
                    Runtime.getRuntime().exec(new String[]{"xdg-open", url});
                }
                System.out.println("✓ 浏览器已打开");
            } catch (Exception e) {
                System.out.println("⚠ 无法自动打开浏览器，请手动访问上述地址");
            }
        }
        
        System.out.println("注意：OAuth 登录功能尚未完全实现");
        System.out.println("建议先使用 API Key 方式登录：/login apikey -k <your-api-key>");
    }
}