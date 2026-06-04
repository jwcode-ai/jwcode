package com.jwcode.core.tool;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class PowerShellTool implements Tool<PowerShellTool.Input, PowerShellTool.Output, PowerShellTool.Progress> {
    @Override public String getName() { return "PowerShell"; }
    @Override public String getDescription() { return "执行 PowerShell 命令"; }
    @Override public String getPrompt() { return "Use PowerShellTool to execute Windows PowerShell commands."; }

public static class Input { 
        public String command; 
        
        public Input() {}
        public Input(String command) { this.command = command; }
    }
    
    public static class Output { 
        public boolean success; 
        public String output; 
        public String error;
        public int exitCode;
    }
    
    public static class Progress {}
}
