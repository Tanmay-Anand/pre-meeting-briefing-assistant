/**
 * Wire types for leads-crm-backend's mounted ai-query-sdk instance
 * (POST /ai-sdk/auth/token, POST /ai-sdk/query). Mirrors the shapes in that SDK's
 * com.leadrat.aisdk.query package - see java-sdk/src/main/java/com/leadrat/aisdk/query/
 * QueryRequest.java, QueryResponse.java and TraversalResult.java.
 */

export interface AiSdkTokenResponse {
  token: string
  expiresIn: number
}

export interface AiSdkTarget {
  entity: string
  id: string
}

export interface AiSdkQueryOptions {
  childDepth?: number
  parentDepth?: number
  maxChildrenPerRelation?: number
}

export interface AiSdkQueryRequest {
  question: string
  targets: AiSdkTarget[]
  options?: AiSdkQueryOptions
}

export interface AiSdkParentNode {
  level: number
  relation: string
  entity: string
  data: Record<string, unknown>
}

export interface AiSdkChildGroup {
  relation: string
  entity: string
  count: number
  items: Record<string, unknown>[]
}

export interface AiSdkNode {
  self: Record<string, unknown>
  parents: AiSdkParentNode[]
  children: AiSdkChildGroup[]
}

export interface AiSdkQueryMeta {
  cached: boolean
  generatedAt: string
  plannerModel: string
  summarizerModel: string
  parentDepth: number
  childDepth: number
  latencyMs: number
}

export interface AiSdkQueryResponse {
  answer: string
  targets: AiSdkTarget[]
  data: Record<string, AiSdkNode>
  meta: AiSdkQueryMeta
}
