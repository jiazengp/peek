package com.peek.utils;

import com.mojang.brigadier.context.CommandContext;
import com.peek.data.peek.PeekRequest;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.function.Function;

public class CommandRequestProcessor {
    
    /**
     * 请求处理结果
     */
    public static class RequestResult {
        private final PeekRequest request;
        private final boolean success;
        private final String errorMessage;
        
        private RequestResult(PeekRequest request, boolean success, String errorMessage) {
            this.request = request;
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public static RequestResult success(PeekRequest request) {
            return new RequestResult(request, true, null);
        }
        
        public static RequestResult failure(String errorMessage) {
            return new RequestResult(null, false, errorMessage);
        }
        
        public PeekRequest getRequest() { return request; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * 处理带有可选requester参数的请求(Accept/Deny)
     * 统一处理重复的验证逻辑
     * 
     * @param context 命令上下文
     * @param player 执行命令的玩家
     * @param requesterArgName requester参数名称(通常是"requester")
     * @return 处理结果
     */
    public static RequestResult processRequestWithOptionalRequester(
            CommandContext<ServerCommandSource> context,
            ServerPlayerEntity player,
            String requesterArgName) {
        
        PeekRequest request;
        
        // 尝试获取requester参数
        try {
            ServerPlayerEntity requester = CommandUtils.getPlayerArgument(context, requesterArgName);
            request = ValidationUtils.validatePendingRequest(player.getUuid(), player);
            
            if (requester != null) {
                // 验证特定玩家是否发送了请求
                if (request == null || !request.getRequesterId().equals(requester.getUuid())) {
                    String errorMsg = Text.translatable("peek.error.no_request_from_player", 
                        requester.getName().getString()).getString();
                    return RequestResult.failure(errorMsg);
                }
            } else {
                // 没有指定requester，使用第一个待处理请求
                if (request == null) {
                    return RequestResult.failure("No pending request found");
                }
            }
            
        } catch (Exception e) {
            // 没有提供requester参数，使用现有逻辑
            request = ValidationUtils.validatePendingRequest(player.getUuid(), player);
            if (request == null) {
                return RequestResult.failure("No pending request found");
            }
        }
        
        return RequestResult.success(request);
    }
    
    /**
     * 执行请求操作的通用模板方法
     * 
     * @param context 命令上下文
     * @param requesterArgName requester参数名称
     * @param requestProcessor 请求处理函数(接受player和request，返回是否成功)
     * @return 命令执行结果(1=成功, 0=失败)
     */
    public static int executeRequestCommand(
            CommandContext<ServerCommandSource> context,
            String requesterArgName,
            Function<RequestProcessorInput, Boolean> requestProcessor) {
        
        return CommandUtils.executePlayerCommand(context, (player) -> {
            // 1. 获取并验证请求
            RequestResult result = processRequestWithOptionalRequester(context, player, requesterArgName);
            
            if (!result.isSuccess()) {
                // 发送错误消息
                player.sendMessage(Text.literal("§c" + result.getErrorMessage()), false);
                return 0;
            }
            
            // 2. 执行具体的请求处理逻辑
            RequestProcessorInput input = new RequestProcessorInput(player, result.getRequest());
            boolean success = requestProcessor.apply(input);
            
            return success ? 1 : 0;
        });
    }
    
    /**
     * 请求处理器的输入参数封装
     */
    public static class RequestProcessorInput {
        private final ServerPlayerEntity player;
        private final PeekRequest request;
        
        public RequestProcessorInput(ServerPlayerEntity player, PeekRequest request) {
            this.player = player;
            this.request = request;
        }
        
        public ServerPlayerEntity getPlayer() { return player; }
        public PeekRequest getRequest() { return request; }
    }
}