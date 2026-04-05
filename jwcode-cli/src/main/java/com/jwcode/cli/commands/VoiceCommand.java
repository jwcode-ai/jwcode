package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * VoiceCommand - /voice 命令
 * 
 * 功能说明：
 * 语音模式，启用语音输入和输出功能。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/voice", description = "语音模式")
public class VoiceCommand implements Runnable {
    
    @Option(names = {"-e", "--enable"}, description = "启用语音模式")
    private boolean enable;
    
    @Option(names = {"-d", "--disable"}, description = "禁用语音模式")
    private boolean disable;
    
    @Option(names = {"-s", "--status"}, description = "查看当前状态")
    private boolean status;
    
    @Option(names = {"-i", "--input"}, description = "设置语音输入设备")
    private String inputDevice;
    
    @Option(names = {"-o", "--output"}, description = "设置语音输出设备")
    private String outputDevice;
    
    @Option(names = {"-l", "--list"}, description = "列出可用音频设备")
    private boolean listDevices;
    
    private static boolean voiceModeEnabled = false;
    private static String currentInputDevice = "default";
    private static String currentOutputDevice = "default";
    
    @Override
    public void run() {
        if (listDevices) {
            listAudioDevices();
            return;
        }
        
        if (status || (!enable && !disable && inputDevice == null && outputDevice == null)) {
            showStatus();
            return;
        }
        
        if (enable) {
            voiceModeEnabled = true;
            System.out.println("语音模式已启用");
            System.out.println();
            showVoiceHelp();
        } else if (disable) {
            voiceModeEnabled = false;
            System.out.println("语音模式已禁用");
        }
        
        if (inputDevice != null) {
            currentInputDevice = inputDevice;
            System.out.println("语音输入设备已设置为：" + inputDevice);
        }
        
        if (outputDevice != null) {
            currentOutputDevice = outputDevice;
            System.out.println("语音输出设备已设置为：" + outputDevice);
        }
    }
    
    private void showStatus() {
        System.out.println("=== 语音模式状态 ===");
        System.out.println();
        System.out.println("语音模式：" + (voiceModeEnabled ? "已启用" : "已禁用"));
        System.out.println("输入设备：" + currentInputDevice);
        System.out.println("输出设备：" + currentOutputDevice);
        System.out.println();
        if (voiceModeEnabled) {
            showVoiceHelp();
        }
    }
    
    private void listAudioDevices() {
        System.out.println("=== 可用音频设备 ===");
        System.out.println();
        System.out.println("输入设备:");
        System.out.println("  default          - 系统默认麦克风");
        System.out.println("  builtin-mic      - 内置麦克风");
        System.out.println("  usb-mic-001      - USB 麦克风");
        System.out.println("  headset-mic      - 耳机麦克风");
        System.out.println();
        System.out.println("输出设备:");
        System.out.println("  default          - 系统默认扬声器");
        System.out.println("  builtin-speaker  - 内置扬声器");
        System.out.println("  usb-speaker-001  - USB 扬声器");
        System.out.println("  headset-output   - 耳机输出");
    }
    
    private void showVoiceHelp() {
        System.out.println("语音模式命令:");
        System.out.println();
        System.out.println("  /voice -e           启用语音模式");
        System.out.println("  /voice -d           禁用语音模式");
        System.out.println("  /voice -i <device>  设置输入设备");
        System.out.println("  /voice -o <device>  设置输出设备");
        System.out.println("  /voice -l           列出设备");
        System.out.println();
        System.out.println("语音控制:");
        System.out.println("  '开始录音'     - 开始语音输入");
        System.out.println("  '停止录音'     - 停止语音输入");
        System.out.println("  '朗读'         - 朗读当前内容");
        System.out.println("  '停止朗读'     - 停止朗读");
    }
    
    public static boolean isVoiceModeEnabled() {
        return voiceModeEnabled;
    }
    
    public static String getCurrentInputDevice() {
        return currentInputDevice;
    }
    
    public static String getCurrentOutputDevice() {
        return currentOutputDevice;
    }
}