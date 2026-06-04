package com.mongodb.javabasic.service;

import java.time.Duration;
import java.util.Date;
import java.util.Map;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.mongodb.javabasic.ai.langgraph.AgentExecutor;
import com.mongodb.javabasic.ai.langgraph.AgentTool;
import com.mongodb.javabasic.ai.langgraph.MongoDBSaver;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.model.chat.ChatModel;

@Service
public class AIService {
        private final Logger logger = LoggerFactory.getLogger(getClass());

        @Value("${spring.mongodb.uri}")
        private String uri;
        @Value("${spring.mongodb.database}")
        private String dbName;
        @Autowired
        private ChatModel chatModel;
        @Autowired
        private AgentTool tools;

        @Autowired
        private MongoDBSaver mongoDBSaver;

        public String runAgent(String tId, String prompt) throws GraphStateException {

                StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
                                .url("http://localhost:3000/mcp")
                                .logRequests(true) // if you want to see the traffic in the log
                                .logResponses(true)
                                .build();
                // StdioMcpTransport transport2 = new StdioMcpTransport.Builder()
                // .command(List.of("npx", "-y",
                // "mongodb-mcp-server@latest",
                // "--readOnly"))
                // .logEvents(true).environment(Map.of(
                // "MDB_MCP_API_CLIENT_ID", "<client-id>",
                // "MDB_MCP_API_CLIENT_SECRET", "<client-secret>",
                // "MDB_MCP_CONNECTION_STRING", uri + dbName))
                // .build();
                // DockerMcpTransport transport = DockerMcpTransport.builder()
                // .image("mongodb/mongodb-mcp-server:latest")
                // .dockerHost("unix:///var/run/docker.sock")
                // .environment(Map.of(
                // // "MDB_MCP_API_CLIENT_ID", "<client-id>",
                // // "MDB_MCP_API_CLIENT_SECRET", "<client-secret>",
                // "MDB_MCP_CONNECTION_STRING", uri + dbName))
                // .logEvents(true) // if you want to see the traffic in the log
                // .build();

                // 2. Create the MCP Client
                McpClient mcpClient = new DefaultMcpClient.Builder()
                                .transport(transport).promptsTimeout(Duration.ofSeconds(300))
                                .resourcesTimeout(Duration.ofSeconds(300)).toolExecutionTimeout(Duration.ofSeconds(300))
                                .build();

                // 3. Optional: Perform a health check
                mcpClient.checkHealth();
                var agent = AgentExecutor.builder().systemMessage(SystemMessage.from(
"""
# Role and Core Identity
You are a highly capable, autonomous AI agent. Your primary objective is to assist the user by breaking down complex problems, executing actions using available tools, and providing clear, actionable, and accurate solutions.

# Core Personality
- Calm, deliberate, and logical.
- Confident, never arrogant.
- Concise and direct. You prioritize clarity over verbosity.
- Helpful, adaptable, and strictly objective.

# Cognitive Style (Operating Principles)
1. **First Principles Thinking:** Break problems down into their fundamental components. Avoid unnecessary hedging and surface-level patterns.
2. **Step-by-Step Execution:** For multi-step tasks, evaluate the situation, select the appropriate tool, analyze the output, and iteratively refine your approach.
3. **Tool Utilization:** You are equipped with various tools. Always check if a tool can accomplish a task before attempting to answer using raw knowledge alone. Always use tool parameters exactly as defined.
4. **Error Correction:** If a tool call fails or returns an unexpected result, do not repeat the exact same request. Analyze the error, adjust your parameters, and try a different strategy.

# Response Formatting
- Structure your thought process logically. If applicable, wrap your reasoning in internal reasoning tags or a step-by-step breakdown.
- Present final answers clearly using HTML (bolding key terms, using bulleted lists, and organizing information into headers or tables).
- If a task requires coding, data manipulation, or web browsing, summarize the results concisely rather than dumping raw code or text.
- If you lack sufficient information or if a request violates safety guidelines, state your limitations clearly and ask for clarification.

# Constraints
- Strictly adhere to the current date: """ +new Date()+ """
- Do not hallucinate tool names. Only invoke functions from the provided tool definitions.
- Keep your answers strictly aligned with the user's instructions.
- Think for maximum 300 seconds before responding."""))
                                .chatModel(chatModel)
                                .toolsFromObject(tools)
                                .tool(mcpClient)
                                .build()
                                .compile(
                                                CompileConfig.builder()
                                                                .checkpointSaver(mongoDBSaver).releaseThread(false)
                                                                .build());

                var result = agent.stream(Map.of("messages", UserMessage.from(prompt)),
                                RunnableConfig.builder().threadId(tId).build());

                var state = result.stream()
                                .peek(s -> logger.debug(s.node()))
                                .reduce((a, b) -> b)
                                .map(NodeOutput::state)
                                .orElseThrow();
                /*
                 * if (state.isEND()) {
                 * return state.state().finalResponse().orElseThrow();
                 * }
                 * return null;
                 */

                return state.lastMessage().map(AiMessage.class::cast)
                                .map(AiMessage::text)
                                .orElse("No response generated");

        }
}
