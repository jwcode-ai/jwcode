import React from 'react';
import { SprintContract, EvaluationReport, useContractStore } from '../../stores/contractStore';

/**
 * SprintContractView — Sprint 合同视图组件。
 *
 * <p>展示 Sprint 合同的谈判、签署、执行状态，
 * 以及每次迭代的评估报告。</p>
 */
const SprintContractView: React.FC<{ sessionId: string }> = ({ sessionId }) => {
  const contracts = useContractStore((state) => state.getContracts(sessionId));
  const activeContractId = useContractStore((state) => state.activeContractId);
  const setActiveContract = useContractStore((state) => state.setActiveContract);
  const getLatestReport = useContractStore((state) => state.getLatestReport);
  const getReports = useContractStore((state) => state.getReports);

  if (contracts.length === 0) {
    return null;
  }

  return (
    <div className="sprint-contract-view space-y-4">
      <div className="flex items-center gap-2 mb-3">
        <span className="text-lg">📋</span>
        <h3 className="text-sm font-semibold text-gray-300">Sprint Contracts</h3>
      </div>

      {contracts.map((contract) => (
        <ContractCard
          key={contract.contractId}
          contract={contract}
          isActive={activeContractId === contract.contractId}
          onClick={() => setActiveContract(
            activeContractId === contract.contractId ? null : contract.contractId
          )}
          latestReport={getLatestReport(contract.contractId)}
          reports={getReports(contract.contractId)}
        />
      ))}
    </div>
  );
};

// ==================== 合同卡片 ====================

interface ContractCardProps {
  contract: SprintContract;
  isActive: boolean;
  onClick: () => void;
  latestReport: EvaluationReport | null;
  reports: EvaluationReport[];
}

const ContractCard: React.FC<ContractCardProps> = ({
  contract,
  isActive,
  onClick,
  latestReport,
  reports,
}) => {
  const statusConfig = getStatusConfig(contract.status);

  return (
    <div
      className={`contract-card rounded-lg border ${
        isActive ? 'border-blue-500 bg-gray-800' : 'border-gray-700 bg-gray-850'
      } p-3 cursor-pointer transition-all hover:border-gray-500`}
      onClick={onClick}
    >
      {/* Header */}
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <span className="text-lg">{statusConfig.icon}</span>
          <span className="text-xs font-mono text-gray-400">
            {contract.contractId}
          </span>
        </div>
        <span
          className={`text-xs px-2 py-0.5 rounded-full ${statusConfig.bgClass} ${statusConfig.textClass}`}
        >
          {statusConfig.label}
        </span>
      </div>

      {/* Feature */}
      <p className="text-sm text-gray-200 mb-2 line-clamp-2">
        {contract.feature}
      </p>

      {/* Iteration Progress */}
      <div className="flex items-center gap-2 mb-2">
        <span className="text-xs text-gray-400">
          迭代: {contract.currentIteration}/{contract.maxIterations}
        </span>
        <div className="flex-1 h-1.5 bg-gray-700 rounded-full overflow-hidden">
          <div
            className="h-full bg-blue-500 rounded-full transition-all"
            style={{
              width: `${(contract.currentIteration / contract.maxIterations) * 100}%`,
            }}
          />
        </div>
      </div>

      {/* Signature Status */}
      <div className="flex gap-3 text-xs text-gray-400">
        <span>
          Generator: {contract.signedByGenerator ? '✅' : '⏳'}
        </span>
        <span>
          Evaluator: {contract.signedByEvaluator ? '✅' : '⏳'}
        </span>
      </div>

      {/* Expanded Content */}
      {isActive && (
        <div className="mt-3 pt-3 border-t border-gray-700 space-y-3">
          {/* Acceptance Criteria */}
          {contract.acceptanceCriteria.length > 0 && (
            <div>
              <h4 className="text-xs font-semibold text-gray-400 mb-1">
                验收标准
              </h4>
              <ul className="space-y-0.5">
                {contract.acceptanceCriteria.map((criterion, i) => (
                  <li key={i} className="text-xs text-gray-300 flex items-start gap-1">
                    <span className="text-gray-500 mt-0.5">•</span>
                    {criterion}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Scoring Weights */}
          {Object.keys(contract.scoringWeights).length > 0 && (
            <div>
              <h4 className="text-xs font-semibold text-gray-400 mb-1">
                评分权重
              </h4>
              <div className="grid grid-cols-2 gap-1">
                {Object.entries(contract.scoringWeights).map(([dim, weight]) => (
                  <div
                    key={dim}
                    className="text-xs text-gray-300 flex justify-between px-2 py-0.5 bg-gray-750 rounded"
                  >
                    <span>{getDimensionLabel(dim)}</span>
                    <span className="text-gray-400">{weight.toFixed(2)}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Latest Evaluation Report */}
          {latestReport && (
            <div>
              <h4 className="text-xs font-semibold text-gray-400 mb-1">
                最新评估 (迭代 #{latestReport.iterationRound})
              </h4>
              <div className="bg-gray-750 rounded p-2 space-y-1">
                <div className="flex items-center justify-between">
                  <span className="text-xs text-gray-300">
                    {getVerdictDisplay(latestReport.verdict)}
                  </span>
                  <span className="text-xs font-mono text-gray-400">
                    {latestReport.weightedTotalScore.toFixed(2)}/10.0
                  </span>
                </div>

                {/* Dimension Scores */}
                {latestReport.scores.map((score) => (
                  <div key={score.dimension} className="flex items-center gap-2">
                    <span className="text-xs text-gray-400 w-20 truncate">
                      {score.dimensionDisplayName}
                    </span>
                    <div className="flex-1 h-1.5 bg-gray-700 rounded-full overflow-hidden">
                      <div
                        className={`h-full rounded-full transition-all ${
                          score.passed ? 'bg-green-500' : 'bg-red-500'
                        }`}
                        style={{ width: `${(score.score / 10) * 100}%` }}
                      />
                    </div>
                    <span className={`text-xs font-mono w-8 text-right ${
                      score.passed ? 'text-green-400' : 'text-red-400'
                    }`}>
                      {score.score.toFixed(1)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* All Reports Summary */}
          {reports.length > 1 && (
            <div>
              <h4 className="text-xs font-semibold text-gray-400 mb-1">
                迭代历史
              </h4>
              <div className="space-y-0.5">
                {reports.map((report, i) => (
                  <div
                    key={i}
                    className="flex items-center justify-between text-xs text-gray-400 px-2 py-0.5 bg-gray-750 rounded"
                  >
                    <span>迭代 #{report.iterationRound}</span>
                    <span>{getVerdictDisplay(report.verdict)}</span>
                    <span className="font-mono">
                      {report.weightedTotalScore.toFixed(2)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

// ==================== 辅助函数 ====================

function getStatusConfig(status: SprintContract['status']) {
  switch (status) {
    case 'DRAFT':
      return { icon: '📄', label: '草稿', bgClass: 'bg-gray-700', textClass: 'text-gray-300' };
    case 'NEGOTIATING':
      return { icon: '🤝', label: '谈判中', bgClass: 'bg-yellow-700', textClass: 'text-yellow-200' };
    case 'SIGNED':
      return { icon: '✍️', label: '已签署', bgClass: 'bg-blue-700', textClass: 'text-blue-200' };
    case 'EXECUTING':
      return { icon: '🚀', label: '执行中', bgClass: 'bg-green-700', textClass: 'text-green-200' };
    case 'COMPLETED':
      return { icon: '✅', label: '已完成', bgClass: 'bg-green-700', textClass: 'text-green-200' };
    case 'FAILED':
      return { icon: '❌', label: '失败', bgClass: 'bg-red-700', textClass: 'text-red-200' };
  }
}

function getVerdictDisplay(verdict: string): string {
  switch (verdict) {
    case 'PASS': return '✅ PASS';
    case 'CONDITIONAL_PASS': return '⚠️ CONDITIONAL';
    case 'FAIL': return '❌ FAIL';
    case 'CRITICAL_FAIL': return '🚫 CRITICAL';
    default: return verdict;
  }
}

function getDimensionLabel(key: string): string {
  switch (key) {
    case 'product_depth': return '产品深度';
    case 'functionality': return '功能性';
    case 'visual_design': return '视觉设计';
    case 'code_quality': return '代码质量';
    default: return key;
  }
}

export default SprintContractView;
