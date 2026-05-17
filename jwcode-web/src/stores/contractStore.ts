import { create } from 'zustand';

/**
 * SprintContract 的前端状态模型。
 *
 * <p>对应后端 SprintContract.java 的字段，
 * 用于在前端展示合同谈判、签署和执行状态。</p>
 */
export interface SprintContract {
  contractId: string;
  taskId: string;
  feature: string;
  acceptanceCriteria: string[];
  scoringWeights: Record<string, number>;
  thresholds: Record<string, number>;
  maxIterations: number;
  currentIteration: number;
  status: 'DRAFT' | 'NEGOTIATING' | 'SIGNED' | 'EXECUTING' | 'COMPLETED' | 'FAILED';
  signedByGenerator: boolean;
  signedByEvaluator: boolean;
  handoffArtifactPath?: string;
  createdAt: string;
  signedAt?: string;
  completedAt?: string;
}

/**
 * EvaluationReport 的前端状态模型。
 */
export interface EvaluationReport {
  contractId: string;
  iterationRound: number;
  scores: EvaluationScore[];
  verdict: 'PASS' | 'CONDITIONAL_PASS' | 'FAIL' | 'CRITICAL_FAIL';
  weightedTotalScore: number;
  summary: string;
  thresholdFailures: string[];
  evaluatedAt: string;
}

/**
 * EvaluationScore 的前端状态模型。
 */
export interface EvaluationScore {
  dimension: string;
  dimensionDisplayName: string;
  score: number;
  weight: number;
  threshold: number;
  passed: boolean;
  evidence: string;
  failures: string[];
  suggestions: string[];
}

/**
 * 合同状态管理 Store。
 */
interface ContractState {
  // 按 sessionId 隔离的合同
  contractsBySession: Record<string, SprintContract[]>;

  // 按合同ID隔离的评估报告
  reportsByContract: Record<string, EvaluationReport[]>;

  // 当前选中的合同ID
  activeContractId: string | null;

  // Actions
  setContracts: (sessionId: string, contracts: SprintContract[]) => void;
  updateContract: (sessionId: string, contractId: string, updates: Partial<SprintContract>) => void;
  getContracts: (sessionId: string) => SprintContract[];
  getContract: (sessionId: string, contractId: string) => SprintContract | null;

  addReport: (contractId: string, report: EvaluationReport) => void;
  getReports: (contractId: string) => EvaluationReport[];
  getLatestReport: (contractId: string) => EvaluationReport | null;

  setActiveContract: (contractId: string | null) => void;
}

export const useContractStore = create<ContractState>((set, get) => ({
  contractsBySession: {},
  reportsByContract: {},
  activeContractId: null,

  setContracts: (sessionId: string, contracts: SprintContract[]) => {
    set((state) => ({
      contractsBySession: {
        ...state.contractsBySession,
        [sessionId]: contracts,
      },
    }));
  },

  updateContract: (sessionId: string, contractId: string, updates: Partial<SprintContract>) => {
    set((state) => {
      const contracts = state.contractsBySession[sessionId] || [];
      return {
        contractsBySession: {
          ...state.contractsBySession,
          [sessionId]: contracts.map((c) =>
            c.contractId === contractId ? { ...c, ...updates } : c
          ),
        },
      };
    });
  },

  getContracts: (sessionId: string) => {
    return get().contractsBySession[sessionId] || [];
  },

  getContract: (sessionId: string, contractId: string) => {
    const contracts = get().contractsBySession[sessionId] || [];
    return contracts.find((c) => c.contractId === contractId) || null;
  },

  addReport: (contractId: string, report: EvaluationReport) => {
    set((state) => {
      const reports = state.reportsByContract[contractId] || [];
      return {
        reportsByContract: {
          ...state.reportsByContract,
          [contractId]: [...reports, report],
        },
      };
    });
  },

  getReports: (contractId: string) => {
    return get().reportsByContract[contractId] || [];
  },

  getLatestReport: (contractId: string): EvaluationReport | null => {
    const reports = get().reportsByContract[contractId] || [];
    const last = reports[reports.length - 1];
    return last !== undefined ? last : null;
  },

  setActiveContract: (contractId: string | null) => {
    set({ activeContractId: contractId });
  },
}));
