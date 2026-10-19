import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { GoogleGenAI } from "@google/genai";
import { z } from "zod";

const apiKey = process.env.GEMINI_API_KEY;

if (!apiKey) {
  throw new Error("GEMINI_API_KEY is not set");
}

const ai = new GoogleGenAI({ apiKey });
const model = process.env.GEMINI_MODEL || "gemini-2.5-flash";

const server = new McpServer({
  name: "gemini-worker",
  version: "1.0.0",
});

async function askGemini(role, task, context = "") {
  const prompt = `
당신은 ${role} 역할의 서브 에이전트입니다.

규칙:
- 요청받은 역할에만 집중하세요.
- 불확실한 내용은 추측하지 말고 명시하세요.
- 결과를 명확한 Markdown으로 반환하세요.
- 개발 작업에서도 직접 파일을 수정하지 말고 변경안 또는 unified diff를 반환하세요.

작업:
${task}

추가 컨텍스트:
${context || "없음"}
`;

  const response = await ai.models.generateContent({
    model,
    contents: prompt,
  });

  return response.text || "Gemini returned an empty response.";
}

function textResult(text) {
  return {
    content: [{ type: "text", text }],
  };
}

server.tool(
  "gemini_plan",
  "Gemini에게 기획과 요구사항 분석을 요청합니다.",
  {
    task: z.string().describe("기획 또는 분석할 작업"),
    context: z.string().optional().describe("저장소 구조, 요구사항 등 추가 컨텍스트"),
  },
  async ({ task, context }) =>
    textResult(await askGemini("제품 기획자이자 시스템 분석가", task, context)),
);

server.tool(
  "gemini_develop",
  "Gemini에게 구현 방법과 코드 변경안을 요청합니다.",
  {
    task: z.string().describe("구현할 작업"),
    context: z.string().optional().describe("관련 파일 내용, 계획, 제약사항 등"),
  },
  async ({ task, context }) =>
    textResult(await askGemini("시니어 소프트웨어 개발자", task, context)),
);

server.tool(
  "gemini_review",
  "Gemini에게 코드 리뷰와 테스트 검토를 요청합니다.",
  {
    task: z.string().describe("리뷰할 내용 또는 리뷰 요청"),
    context: z.string().optional().describe("git diff, 요구사항, 테스트 결과 등"),
  },
  async ({ task, context }) =>
    textResult(await askGemini("코드 리뷰어이자 QA 엔지니어", task, context)),
);

const transport = new StdioServerTransport();
await server.connect(transport);

console.error(`gemini-worker MCP server started with model: ${model}`);
