# JWCode 鏋舵瀯瑙勮寖

> 鏈枃妗ｄ负 JWCode 椤圭洰鐨?AI 鍗忎綔瑙勮寖鍜?Agent 鏋舵瀯瀹氫箟銆?> 鎵€鏈夊満鏅繀椤讳弗鏍兼墽琛岋細**涓籄gent鎷嗚В璋冨害锛屽瓙Agent鎵ц宸ヤ綔**銆?> 鍩轰簬 Harness Engineering 妗嗘灦 (R.E.S.T 妯″瀷) 鏋勫缓銆?> 鏈€鍚庤嚜鍔ㄦ洿鏂帮細2026-05-26 | 鐗堟湰 1.0.0-SNAPSHOT | 5 妯″潡 | 17 Agent | 47 Tool

---

## -1. 鏋勫缓涓庤繍琛?
```bash
mvn compile -pl jwcode-core,jwcode-web -am -q   # 缂栬瘧
mvn test -pl jwcode-core -am                      # 娴嬭瘯
./start.bat / ./start.sh                          # 涓€閿惎鍔?jwcode start                                      # Python CLI 鍏ㄨ嚜鍔?```

**鍓嶅悗绔?*: Python CLI (Rich+Textual) / React Web UI (Vite+Tailwind, :8080) / VS Code 鎻掍欢 (Ctrl+Shift+J)

---

## 0. Harness Engineering 鍥涘眰鏋舵瀯 (v3.1)

jwcode 浠?R.E.S.T 妯″瀷鏋勫缓 Agent 椹鹃┉浣撶郴锛?
```
L1 瀹夊叏:  DockerSandbox 鈫?WorkspaceGuard 鈫?PermissionManager 鈫?HookChain 鈫?AuditLogger
L2 鎴愭湰:  ModelRouter + CostTracker + Prompt Caching + TokenBudget 鍒嗗尯
L3 璐ㄩ噺:  浜旂骇鍘嬬缉淇濈暀 + AiRepair 鑷剤 + ZONE 娉ㄥ叆杈圭晫 + 璇箟璁板繂 + 瀛愪唬鐞嗛殧绂?L4 鍙娴? AnalyticsObserver + /doctor + /rewind + ProjectDocGenerator
```

**鍏ぇ璁捐鍘熷垯**: 涓哄け璐ヨ€岃璁?| 濂戠害浼樺厛 | 榛樿瀹夊叏 | 鍐崇瓥涓庢墽琛屽垎绂?| 涓囩墿鐨嗗彲搴﹂噺 | 鏁版嵁椹卞姩杩涘寲

**绂佹**: Orchestrator 鐩存帴鎵ц宸ュ叿 | 瀛?Agent 閫掑綊鍒涘缓瀛?Agent | Reviewer/Explorer 淇敼鏂囦欢 | LLM 缁存姢璺ㄨ疆娆＄姸鎬?
### 鍏抽敭鏂囦欢閫熸煡

| 缁勪欢 | 鏂囦欢 |
|------|------|
| LLM/Tool/Hook | `llm/LLMQueryEngine.java` `tool/ToolExecutor.java` `hook/HookChain.java` |
| Agent 绯荤粺 | `agent/EnhancedOrchestratorAgent.java` `agent/AgentRegistry.java` |
| 娌欑/璺敱 | `tool/shell/DockerSandboxExecutor.java` `llm/ModelRouter.java` |
| 鎴愭湰/璇婃柇 | `service/CostTrackerService.java` `service/DoctorService.java` |
| 鍘嬬缉/鑷剤 | `service/SimpleCompactionStrategy.java` `resilience/RecoveryExecutor.java` |
| 鏂囨。/璁板繂 | `service/ProjectDocGenerator.java` `agent/WorkspaceMemoryStore.java` |
| WS/鍓嶇 | `jwcode-web/.../stream/StreamingWebSocketHandler.java` `python-cli/jwcode/main.py` |
| CI/鎻掍欢 | `.github/workflows/ci.yml` `vscode-extension/src/extension.ts` |
| 閰嶇疆 | `~/.jwcode/config.yaml` |

---

## 1. 閮ㄧ讲鏋舵瀯 (v3.0)

```
          鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹? Hook 鎷︽埅浣撶郴锛坴2.1 鏂板锛岀敓鍛藉懆鏈熸嫤鎴笌杩愯鏃舵不鐞嗭級          鈹?鈹? 鑱岃矗锛氬湪鍏抽敭鑺傜偣鎷︽埅銆佸喅绛栥€佷慨鏀规墽琛屾祦                          鈹?鈹? 浜嬩欢锛?2绉嶇敓鍛藉懆鏈熶簨浠?(Session/Tool/Context/StateMachine/   鈹?鈹?       Task/A2A)                                             鈹?鈹? 鍐崇瓥锛?绉嶅喅绛栬涔?(ALLOW/DENY/ASK/MODIFY/DEFER/VOID)        鈹?鈹? 褰㈡€侊細4绉嶅疄鐜?(Shell/HTTP/Prompt/Agent Hook)                鈹?鈹? 鏍稿績锛欻ookChain鎷︽埅閾?+ TransitionGuard鐘舵€佸畧鎶?+             鈹?鈹?       A2A杩滅▼鎷︽埅 + Priority-LRU鍐茬獊瑁佸喅                     鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?```

---

## 2. 瑙掕壊瀹氫箟

### 2.1 Orchestrator锛堜富Agent / 绗?灞傦級

| 缁村害 | 璇存槑 |
|------|------|
| **瀹氫綅** | 鍞竴鐢ㄦ埛鍏ュ彛锛屼换鍔℃寚鎸ュ |
| **鑱岃矗** | 鎰忓浘璇嗗埆銆佸鏉傚害璇勪及銆佷换鍔℃媶瑙ｃ€丄gent閫夊瀷銆佸苟琛岀紪鎺掋€佺粨鏋滈獙鏀躲€佹暣鍚堣緭鍑?|
| **鍙敤宸ュ叿** | `AgentTool`锛堟牳蹇冿級銆乣SmartAnalyzeTool`锛堝畯瑙傚垎鏋愶級銆乣AskUserQuestionTool`锛堥渶姹傛緞娓咃級 |
| **绂佹宸ュ叿** | `FileReadTool`銆乣FileWriteTool`銆乣FileEditTool`銆乣BashTool`銆乣PowerShellTool`銆乣GlobTool`銆乣GrepTool` 绛夋墍鏈変笟鍔℃墽琛屽伐鍏?|
| **鍏抽敭鍘熷垯** | 缁濅笉鑷繁鍔ㄦ墜銆傚摢鎬曟槸涓€涓畝鍗曠殑鏂囦欢璇诲彇锛屼篃蹇呴』娲剧粰瀛怉gent銆?|
| **宸ヤ綔妯″紡** | **PDCA寰幆**锛歅lan(浠诲姟娓呭崟) 鈫?Do(涓嬪彂瀛愪换鍔? 鈫?Check(鑱氬悎鐘舵€? 鈫?Act(閲嶈鍒?瀹屾垚) |

### 2.2 涓撲笟Agent锛圵orker / 绗?灞傦級

| Agent | 绫诲瀷 | 鑱岃矗 | 鐗圭偣 |
|-------|------|------|------|
| **Coder** | worker | 浠ｇ爜缂栧啓銆侀噸鏋勩€丅ug淇 | 鍏ㄥ伐鍏锋潈闄愶紙闄ゅ嵄闄╁伐鍏凤級 |
| **Debug** | worker | 閿欒鎺掓煡銆佹牴鍥犲垎鏋愩€佷慨澶嶉獙璇?| 渚ч噸鍒嗘瀽绫诲伐鍏?|
| **Reviewer** | worker | 浠ｇ爜瀹℃煡銆佸畨鍏ㄦ壂鎻忋€侀鏍兼鏌?| **鍙妯″紡**锛屼笉淇敼鏂囦欢 |
| **Evaluator** | worker | GAN寮忚瘎浼颁笓瀹讹紙鍒ゅ埆鍣級锛?缁村姞鏉冭瘎鍒?纭棬妲涘惁鍐筹紝椹卞姩鐢熸垚-璇勪及瀵规姉寰幆 | **鍙妯″紡**锛岃緭鍑虹粨鏋勫寲璇勫垎JSON |
| **Tester** | worker | 娴嬭瘯鐢ㄤ緥璁捐銆佹祴璇曠紪鍐欍€佹墽琛?| 鍙繍琛岀紪璇戝拰娴嬭瘯鍛戒护 |
| **Documenter** | worker | 鏂囨。缂栧啓銆丷EADME銆丄PI鏂囨。 | 璇诲啓鏂囦欢锛屼笉鎵ц鍛戒护 |
| **Explorer** | worker | 浠ｇ爜搴撹皟鐮斻€佺粨鏋勫垎鏋愩€佹妧鏈€哄姟 | **鍙妯″紡**锛岀函璋冪爺 |
| **Architect** | worker | 鏋舵瀯璁捐銆佹帴鍙ｅ畾涔夈€佹妧鏈€夊瀷 | 杈撳嚭璁捐鏂囨。鍜屼唬鐮侀鏋?|
| **TaskAgent** | internal | AI鍥炲鈫掔粨鏋勫寲浠诲姟瑙ｆ瀽锛堥樁娈?妯″紡/渚濊禆锛?| 鏃犲閮ㄥ伐鍏凤紝绾В鏋?|
| **TaskExecutionAgent** | internal | 缁撴瀯鍖栦换鍔￠€愭鎵ц锛堝苟鍙?涓茶璋冨害锛?| 閫氳繃 A2AFacade 璋冨害瀛怉gent |
| **MemoryAgent** | internal | 宸ヤ綔鐩綍绾т富鍔ㄨ蹇嗭紙椤圭洰妯″紡/娲炲療/鍋忓ソ锛?| 鏂囦欢璇诲啓锛堜粎 .jwcode/memory/锛?|
| **Default** | worker | 閫氱敤浠诲姟銆侀檷绾у厹搴?| 鍏ㄥ伐鍏锋潈闄?|

### 2.3 Tool Agent锛堟墽琛屽櫒 / 绗?灞傦級

| 缁村害 | 璇存槑 |
|------|------|
| **瀹氫綅** | 宸ュ叿鎵ц涓撳锛孧CP鍗忚璋冪敤鑰?|
| **鑱岃矗** | 鎺ユ敹鍏蜂綋鍛戒护銆佽皟鐢∕CP宸ュ叿銆佹墽琛屻€佸け璐ヨ嚜璇婃柇涓庝慨澶?|
| **鏍稿績鏈哄埗** | 3娆¤嚜淇寰幆锛屽け璐ヨ繑鍥炵粨鏋勫寲閿欒鎽樿锛堥潪鍘熷鍫嗘爤锛?|
| **杩斿洖鍐呭** | 閿欒绫诲瀷 + 淇灏濊瘯娆℃暟 + 鏈€缁堝け璐ュ師鍥?1鍙ヨ瘽) |
| **涓嶈繑鍥炲唴瀹?* | 鍘熷鍛戒护銆佸爢鏍堣窡韪€佸伐鍏疯鎯?|

### 2.4 CompactorAgent锛堟í鍚戞湇鍔gent / 绗?.5灞傦級

| 缁村害 | 璇存槑 |
|------|------|
| **瀹氫綅** | 涓婁笅鏂囧帇缂╀笓瀹讹紝涓烘墍鏈堿gent鎻愪緵鏈嶅姟 |
| **鑱岃矗** | 鍘嬬缉瀵硅瘽鍘嗗彶锛岃妭鐪乀oken棰勭畻 |
| **绛栫暐** | STRUCTURED(鍩轰簬6绾т紭鍏堢骇+鐢熷懡鍛ㄦ湡娓愯繘娣樻卑, v1.1榛樿) / STRUCTURED(寮哄埗XML杈撳嚭, 鎸変紭鍏堢骇淇濈暀) / SMART(鏅鸿兘鍘嬬缉, 淇濈暀灏鹃儴8鏉?鎽樿) / AGGRESSIVE(婵€杩涘帇缂? 淇濈暀灏鹃儴4鏉?鎽樿) / MINIMAL(鏈€灏忓帇缂? 浠呯Щ闄ゅ櫔澹? |
| **缁撴瀯鍖栬緭鍑?* | `<current_focus>` / `<environment>` / `<completed_tasks>` / `<active_issues>` / `<code_state>` / `<design_decisions>` / `<todo_items>` |
| **Markdown output**: Standard Markdown sections (Current Focus, Environment, Completed Tasks, Active Issues, Code State, Design Decisions, Todo Items) |
| **浼樺厛绾т綋绯?* | 褰撳墠浠诲姟鐘舵€?> 閿欒涓庤В鍐虫柟妗?> 浠ｇ爜鏈€缁堢増鏈?> 绯荤粺涓婁笅鏂?> 璁捐鍐崇瓥 > TODO椤?|
| **AICL浼樺厛绾?* | pinned(姘稿浐) > critical(鍏抽敭) > high(楂? > medium(涓? > low(浣? > optional(鍙€? |
| **瑙﹀彂鏂瑰紡** | Token姘翠綅绾胯嚜鍔ㄨЕ鍙?/ 鐢ㄦ埛鎵嬪姩瑙﹀彂(/compact) / Agent涓诲姩璇锋眰 / 妫€鏌ョ偣鍓?/ 瀛愪换鍔″畬鎴愭椂 / 浼氳瘽瓒呴檺 |

### 2.5 ContextResetManager锛坴3.0 鏂板 / 妯悜鍩虹璁炬柦锛?
Context Reset 鍗忚瀹炵幇"杩涚▼绾ч噸鍚?缁撴瀯鍖栦氦鎺?锛岀敤浜庤В鍐?Agent 涓婁笅鏂囩劍铏戦棶棰樸€?
| 缁村害 | 璇存槑 |
|------|------|
| **瀹氫綅** | 涓婁笅鏂囬噸缃笓瀹讹紝涓烘墍鏈堿gent鎻愪緵杩涚▼绾ч噸鍚湇鍔?|
| **鑱岃矗** | 妫€娴嬩笂涓嬫枃鍘嬪姏銆佺敓鎴愪氦鎺ユ枃妗?HandoffArtifact)銆佽Е鍙慉gent閲嶅惎 |
| **瑙﹀彂鏉′欢** | Token浣跨敤鐜?85%(纭槇鍊? / 杩唬寰幆>3杞?杞槇鍊? / 浠诲姟闃舵鍒囨崲 / Agent涓诲姩璇锋眰 |
| **鏍稿績鏈哄埗** | 鍐荤粨鐘舵€?鈫?鎻愬彇HandoffArtifact 鈫?淇濆瓨鍒癭.jwcode/handoff/` 鈫?鏉€姝绘棫Agent 鈫?鍚姩鏂癆gent 鈫?璇诲彇鎭㈠ |
| **涓嶤ompactor鍏崇郴** | Compactor鍏堝皾璇曞帇缂╋紝鍘嬬缉涓嶈冻浠ヨВ鍐抽棶棰樻椂寤鸿鍗囩骇涓篟eset |

**HandoffArtifact 缁撴瀯**锛?| 瀛楁 | 璇存槑 |
|------|------|
| `sessionId` | 浼氳瘽ID |
| `taskId` | 浠诲姟ID |
| `phase` | 褰撳墠闃舵 |
| `completedWork` | 宸插畬鎴愬伐浣滄憳瑕?|
| `currentState` | 褰撳墠鐘舵€侊紙浠ｇ爜鐘舵€併€侀厤缃瓑锛?|
| `pendingItems` | 寰呭姙浜嬮」鍒楄〃 |
| `decisions` | 鍏抽敭鍐崇瓥璁板綍 |
| `codeState` | 浠ｇ爜鍙樻洿鎽樿 |
| `activeIssues` | 娲昏穬闂鍒楄〃 |
| `nextActions` | 涓嬩竴姝ヨ鍔?|

锛坴1.1 鏂板 / 妯悜鍩虹璁炬柦锛?
AICL (Agent Interaction Context Language) 鏄笂涓嬫枃鍧楃殑缁撴瀯鍖栫敓鍛藉懆鏈熺鐞嗗崗璁€傛牳蹇冪粍浠讹細

| 缁勪欢 | 鏂囦欢 | 鑱岃矗 |
|------|------|------|
| `BlockPriority` | `` | 6绾т紭鍏堢骇鏋氫妇锛圤PTIONAL鈫扨INNED锛? 娣樻卑鍔ㄤ綔鏄犲皠 |
| `BlockLifecycle` | `` | 6鐘舵€佺敓鍛藉懆鏈燂紙active鈫抍ompressed鈫抯ummarized鈫抋rchived鈫抎eprecated锛? pinned |
| `ContextBlock` | `` | AICL鍧楁ā鍨嬶紙id/type/priority/state/ttl/lastAccess/generation锛?|
| `ContextControl` | `` | 鎺у埗灞傦紙TokenBudget + EvictionConfig + LifecycleDefaults锛?|
| `ContextAssembler` | `` | **鏍稿績娣樻卑寮曟搸**锛歅riority-LRU閫愮骇娣樻卑 |
| `AICLSerializer` | `` | XML搴忓垪鍖栵紙ContextBlock 鈫?AICL XML锛?|
| `AICLDeserializer` | `` | XML鍙嶅簭鍒楀寲锛圓ICL XML 鈫?AICLContext锛?|
| `AICLContext` | `` | 瀹屾暣涓婁笅鏂囧鍣紙sessionId/turn/control/blocks/checksum锛?|
| `AICLPromptBuilder` | `` | AI瑙ｆ瀽瑙勫垯Prompt鐢熸垚鍣?|

**娣樻卑绠楁硶锛圥riority-LRU锛?*锛?```
褰?used > total * threshold 鏃讹細
  1. 鎸?priority 鍒嗙粍锛屼粠 optional 寮€濮嬪鐞?  2. 鍚屼紭鍏堢骇鍐呮寜 last-access 鎺掑簭锛圠RU锛?  3. 渚濇鎵ц锛?     optional 鈫?鐩存帴鍒犻櫎     low 鈫?褰掓。(淇濈暀鍏冩暟鎹?
     medium 鈫?鎽樿鏇挎崲        high 鈫?鍚屼箟鍘嬬缉(鍘诲啑浣?
     critical 鈫?浠呭垹娉ㄩ噴      pinned 鈫?璺宠繃
  4. 姣忓鐞嗕竴涓潡锛岄噸鏂拌绠?used
  5. 褰?used <= total * stopThreshold 鏃跺仠姝?```

### 2.6 Hook 鎷︽埅浣撶郴锛坴2.1 鏂板 / 妯悜鍩虹璁炬柦锛?
Hook 鏄敓鍛藉懆鏈熺殑"鍒囬潰"锛孲tateMachine 鏄敓鍛藉懆鏈熺殑"楠ㄦ灦"銆?Hook 浣嶄簬 Governance Layer锛屼笌 Permission Modes銆丼andboxing 骞跺垪銆?
| 缁村害 | 璇存槑 |
|------|------|
| **瀹氫綅** | 鐢熷懡鍛ㄦ湡鎷︽埅涓庤繍琛屾椂娌荤悊 |
| **鑱岃矗** | 鍦ㄥ叧閿妭鐐规嫤鎴€佸喅绛栥€佷慨鏀规墽琛屾祦锛堝伐鍏疯皟鐢?鐘舵€佽浆鎹?A2A浠诲姟鍒嗗彂锛?|
| **浜嬩欢妯″瀷** | 12绉嶇敓鍛藉懆鏈熶簨浠讹細SESSION_START/END, PRE_TOOL_USE, POST_TOOL_USE, POST_TOOL_USE_FAILURE, PRE_COMPACT, STATE_TRANSITION, STATE_ENTERED, USER_PROMPT_SUBMIT, SUBAGENT_START, SUBAGENT_STOP, TASK_DISPATCH, A2A_REMOTE_INTERCEPT |
| **鍐崇瓥璇箟** | ALLOW(鏀捐) / DENY(鎷掔粷) / ASK(纭) / MODIFY(淇敼) / DEFER(寤惰繜) / VOID(鍥為€€) |
| **瀹炵幇褰㈡€?* | SHELL(鑴氭湰) / HTTP(REST绔偣) / PROMPT(AI鍔ㄦ€佽瘎浼? / AGENT(瀛愪唬鐞嗚皟鏌? |
| **浼樺厛绾?* | SYSTEM(100) > SECURITY(80) > PROJECT(60) > USER(40) > PLUGIN(20) |
| **鏍稿績鏈哄埗** | HookChain鎷︽埅閾?浼樺厛绾ф帓搴忊啋涓茶鎵ц鈫掔煭璺啋MODIFY閾惧紡浼犻€? + TransitionGuard(鐘舵€佽浆鎹㈠墠缃鎵? + ConflictResolver(鍐茬獊瑁佸喅) |
| **閰嶇疆鏂瑰紡** | `.jwcode/hooks.json` 澹版槑寮忛厤缃紝鏀寔鐑姞杞?|

**鏍稿績缁勪欢**锛?
| 缁勪欢 | 鏂囦欢 | 鑱岃矗 |
|------|------|------|
| `HookDecision` | `hook/HookDecision.java` | 6绉嶅喅绛栬涔夋灇涓?|
| `HookEventType` | `hook/HookEventType.java` | 12绉嶄簨浠剁被鍨嬫灇涓?|
| `HookChain` | `hook/HookChain.java` | **鏍稿績鎷︽埅缂栨帓寮曟搸**锛氭寜浼樺厛绾ф帓搴忋€佷覆琛屾墽琛屻€佺煭璺鍐?|
| `HookRegistry` | `hook/HookRegistry.java` | 閰嶇疆鍔犺浇锛?jwcode/hooks.json锛? 鐑噸杞?+ 浜嬩欢绱㈠紩 |
| `HookExecutor` | `hook/HookExecutor.java` | 鎵ц鍣ㄦ帴鍙ｏ紙Shell/HTTP/Prompt/Agent锛?|
| `HookContext` | `hook/HookContext.java` | 涓婁笅鏂囨暟鎹ā鍨嬶紙鍏叡瀛楁 + 浜嬩欢涓撶敤瀛楁锛?|
| `HookResult` | `hook/HookResult.java` | 鍐崇瓥缁撴灉妯″瀷锛坉ecision/reason/modifiedInput/askPayload锛?|
| `HookAuditLogger` | `hook/HookAuditLogger.java` | 瀹¤鏃ュ織锛圕oncurrentLinkedQueue + 缁熻鎽樿锛?|
| `ShellHookExecutor` | `hook/executor/ShellHookExecutor.java` | stdin JSON 鈫?澶栭儴鑴氭湰 鈫?stdout 鍐崇瓥 |
| `HttpHookExecutor` | `hook/executor/HttpHookExecutor.java` | POST JSON 鈫?REST API 鈫?JSON 鍐崇瓥 |
| `PromptHookExecutor` | `hook/executor/PromptHookExecutor.java` | 妯℃澘 鈫?LLM Prompt 鈫?AI 鍔ㄦ€侀闄╄瘎浼?|
| `AgentHookExecutor` | `hook/executor/AgentHookExecutor.java` | 瀛怉gent(鍙)鈫掓繁搴﹁皟鏌モ啋缁撴瀯鍖栧喅绛?|
| `RollbackAction` | `hook/RollbackAction.java` | 鍥為€€绛栫暐锛圧ETRY/SKIP/ROLLBACK_TO_CHECKPOINT/ABORT锛?|

**涓夊ぇ鎷︽埅鐐?*锛?
```
ToolExecutor.execute()      鈫?PRE_TOOL_USE / POST_TOOL_USE / POST_TOOL_USE_FAILURE
MainAgentStateMachine       鈫?STATE_TRANSITION (TransitionGuard)
LocalAgentDispatcher        鈫?SUBAGENT_START / SUBAGENT_STOP
```

**鍐茬獊瑁佸喅瑙勫垯**锛?```
褰撳涓?Hook 杩斿洖涓嶅悓鍐崇瓥鏃讹細
  1. DENY/VOID 鏈€楂樹紭鍏?鈥?浠讳竴鎷掔粷鍗虫嫆缁?  2. MODIFY 閾惧紡浼犻€?鈥?楂樹紭鍏堢骇鍏堜慨鏀癸紝浣庝紭鍏堢骇鍩轰簬鏂拌緭鍏?  3. ASK 瑕嗙洊 ALLOW 鈥?鍙鏈夌‘璁ら渶姹傦紝鏈€缁堝氨闇€瑕佺‘璁?  4. DEFER 鑱氬悎 鈥?绛夊緟鎵€鏈夊鎵瑰畬鎴?```

---

## 3. 閿欒闅旂锛氫笁灞傛憳瑕佹満鍒?
| 灞傜骇 | 澶辫触鏃惰繑鍥炲唴瀹?| 涓嶈繑鍥炲唴瀹?|
|------|---------------|-----------|
| **Tool Agent** | 閿欒绫诲瀷 + 淇灏濊瘯娆℃暟 + 鏈€缁堝け璐ュ師鍥?1鍙ヨ瘽) | 鍘熷鍛戒护銆佸爢鏍堣窡韪€佸伐鍏疯鎯?|
| **涓撲笟Agent** | 姝ラ澶辫触鎽樿 + 寤鸿鐨勬浛浠ｆ柟妗?| 鍗曟閲嶈瘯杩囩▼銆乀ool Agent鍐呴儴鐘舵€?|
| **涓籄gent** | 浠诲姟鐘舵€?Failed) + 涓氬姟绾уけ璐ュ師鍥?+ 鏄惁闇€瑕佷汉宸ヤ粙鍏?| 浠讳綍鎶€鏈粏鑺?|

### 3.1 閿欒鎽樿妯″瀷 (`ErrorSummary`)

```java
ErrorSummary {
    errorType: String,        // 閿欒绫诲瀷 (TIMEOUT / PERMISSION_DENIED / NOT_FOUND / RATE_LIMIT / INVALID_INPUT / UNKNOWN)
    message: String,          // 涓€鍙ヨ瘽閿欒鎻忚堪
    retryable: boolean,       // 鏄惁鍙噸璇?    attemptCount: int,        // 宸插皾璇曟鏁?    maxRetries: int,          // 鏈€澶ч噸璇曟鏁?    recoveryHint: String,     // 鎭㈠寤鸿锛堝彲閫夛級
    criticalPath: boolean,    // 鏄惁鍏抽敭璺緞
    toBusinessSummary(): String  // 闈㈠悜涓氬姟鐨勪竴鍙ヨ瘽鎽樿
}
```

---

## 4. 鐘舵€佽拷韪細"浠诲姟-姝ラ"鍙屽眰鐘舵€佹満

### 4.1 A2A鏍囧噯浠诲姟鐢熷懡鍛ㄦ湡

```
submitted 鈫?working 鈫?input-required 鈫?completed / failed / canceled
```

### 4.2 鍙屽眰鐘舵€佹満

```
涓籄gent瑙嗚 (Task绾?:
  Task-001: working
    鈹溾攢 SubTask-A (CodeAgent): completed
    鈹溾攢 SubTask-B (DebugAgent): failed  鈫?鏀跺埌鎽樿鍚庡喅绛?    鈹斺攢 SubTask-C (CodeAgent): pending

涓撲笟Agent瑙嗚 (Step绾?:
  SubTask-B: working
    鈹溾攢 Step-1: completed
    鈹溾攢 Step-2: failed (ToolAgent 3娆′慨澶嶅潎澶辫触)
    鈹斺攢 Step-3: pending  鈫?鏍规嵁澶辫触鎽樿鍐冲畾鏄惁璺宠繃/鏇夸唬
```

### 4.3 姝ラ鐘舵€?(`StepStatus`)

```java
enum StepStatus {
    PENDING,      // 寰呮墽琛?    WORKING,      // 鎵ц涓?    COMPLETED,    // 宸插畬鎴?    FAILED,       // 澶辫触
    SKIPPED,      // 璺宠繃
    BLOCKED       // 闃诲
}
```

---

## 5. 閲嶈瘯绛栫暐锛氬垎灞傞檷绾?
### 5.1 涓夊眰閲嶈瘯

```
Tool Agent灞?(鑷慨澶?:
  鎵ц鍛戒护 鈫?澶辫触 鈫?LLM鍒嗘瀽閿欒 鈫?鐢熸垚淇鍛戒护 鈫?閲嶈瘯
  鈫?寰幆3娆′粛澶辫触
  杩斿洖: {status: "FAILED", reason: "鏉冮檺涓嶈冻锛屾棤娉曡闂畑x璧勬簮", retryable: false}

涓撲笟Agent灞?(姝ラ鏇夸唬):
  鏀跺埌Tool Agent澶辫触 鈫?鍒ゆ柇:
    - 濡俽etryable=true: 鎹㈠弬鏁?鎹㈠伐鍏烽噸璇?    - 濡俽etryable=false: 璺宠繃璇ユ楠?鎴?杩斿洖涓籄gent璇锋眰浜哄伐浠嬪叆

涓籄gent灞?(浠诲姟閲嶆帓):
  鏀跺埌瀛愪换鍔″け璐?鈫?鍒ゆ柇:
    - 闈炲叧閿矾寰? 鏍囪閮ㄥ垎瀹屾垚锛岀户缁叾浠栦换鍔?    - 鍏抽敭璺緞: 缁堟娴佺▼锛岃繑鍥炵敤鎴峰け璐ュ師鍥?```

### 5.2 閲嶈瘯绛栫暐 (`RetryStrategy`)

| 绛栫暐 | 璇存槑 | 閫傜敤鍦烘櫙 |
|------|------|---------|
| **鎸囨暟閫€閬?* (榛樿) | 姣忔閲嶈瘯绛夊緟鏃堕棿鎸囨暟澧為暱 | 閫氱敤鍦烘櫙 |
| **鍥哄畾闂撮殧** | 姣忔閲嶈瘯绛夊緟鍥哄畾鏃堕棿 | 璧勬簮绔炰簤鍦烘櫙 |
| **绔嬪嵆閲嶈瘯** | 涓嶇瓑寰呯洿鎺ラ噸璇?| 涓存椂鎬ч敊璇紙瓒呮椂锛?|
| **涓嶉噸璇?* (蹇€熷け璐? | 绔嬪嵆杩斿洖澶辫触 | 鏉冮檺閿欒銆佹棤鏁堣緭鍏?|
| **鑷€傚簲** | 鏍规嵁閿欒绫诲瀷鍔ㄦ€侀€夋嫨 | 缁煎悎鍦烘櫙 |

### 5.3 閲嶈瘯缂栨帓鍣?(`RetryOrchestrator`)

- 鍚屾閲嶈瘯锛歚executeWithRetry(operation, policy, strategy)`
- 寮傛閲嶈瘯锛歚executeWithRetryAsync(operation, policy, strategy)`
- 姝ラ绾у喅绛栵細`decideStepAction(lifecycle, stepId, error, policy, strategy)`
- 浠诲姟绾у喅绛栵細`decideTaskAction(lifecycle, error)`

---

## 6. 涓籄gent鐨凱DCA鍐崇瓥闂幆

```
1. Plan: 鍒嗘瀽鐢ㄦ埛璇锋眰锛岀敓鎴愪换鍔℃竻鍗?Task List)
   鈹斺攢鈫?鎰忓浘璇嗗埆 鈫?澶嶆潅搴﹁瘎浼?鈫?浠诲姟鎷嗚В 鈫?渚濊禆鍒嗘瀽

2. Do: 閫氳繃A2A骞惰/涓茶涓嬪彂瀛愪换鍔?   鈹斺攢鈫?Agent鍙戠幇(Agent Card) 鈫?浠诲姟鎻愪氦 鈫?鐘舵€佽拷韪?
3. Check: 鑱氬悎鍚勫瓙浠诲姟鐘舵€?Artifacts)锛屾鏌ユ槸鍚︽弧瓒崇敤鎴峰師濮嬫剰鍥?   鈹斺攢鈫?鐘舵€佽仛鍚?鈫?缁撴灉楠岃瘉 鈫?鎰忓浘瀵归綈妫€鏌?
4. Act:
   鈹溾攢鈫?鍏ㄩ儴鎴愬姛 鈫?鐢熸垚鏈€缁堝洖澶?   鈹溾攢鈫?閮ㄥ垎澶辫触 鈫?鍩轰簬澶辫触鎽樿閲嶆柊瑙勫垝锛堥潪绠€鍗曢噸璇曪級锛屽彲鑳界敓鎴愯ˉ鍋夸换鍔?   鈹斺攢鈫?瀹屽叏澶辫触 鈫?杩斿洖缁撴瀯鍖栧け璐ユ姤鍛?```

---

## 7. 宸ヤ綔娴佹爣鍑?
### 7.1 浠诲姟澶勭悊娴佺▼

```
1. 鎺ユ敹鐢ㄦ埛璇锋眰
   鈹斺攢鈫?Orchestrator 鍚姩

2. 鎰忓浘璇嗗埆
   鈹斺攢鈫?鍒ゆ柇绫诲瀷锛氬紑鍙?/ 璋冭瘯 / 閲嶆瀯 / 娴嬭瘯 / 鏂囨。 / 鍒嗘瀽

3. 澶嶆潅搴﹁瘎浼?   鈹溾攢鈫?绠€鍗曪紙1-2姝ワ級锛氱洿鎺ユ寚娲?涓瓙Agent
   鈹溾攢鈫?涓瓑锛?-5姝ワ級锛氭媶涓?-3涓瓙浠诲姟锛屽彲骞惰
   鈹斺攢鈫?澶嶆潅锛?5姝ワ級锛氬厛娲?ExploreAgent 璋冪爺锛屽啀鍒跺畾瀹屾暣璁″垝

4. 浠诲姟鎷嗚В锛堢粨鏋勫寲锛?   姣忎釜瀛愪换鍔″繀椤诲寘鍚細
   - task_id: 鍞竴鏍囪瘑
   - task_type: code / review / test / doc / explore / debug / architect
   - description: 璇︾粏鎻忚堪锛堝仛浠€涔堛€佷负浠€涔堛€佽竟鐣岋級
   - acceptance_criteria: 楠屾敹鏍囧噯
   - dependencies: 渚濊禆鐨勫叾浠栦换鍔D
   - context_scope: 闇€鎻愪緵鐨勪笂涓嬫枃鑼冨洿
   - estimated_effort: low / medium / high

5. Agent璋冨害
   鈹斺攢鈫?鐢?AgentTool 鍒涘缓/鍒嗛厤/鎵ц瀛怉gent

6. 骞惰缂栨帓
   鈹溾攢鈫?鏃犱緷璧栫殑浠诲姟 鈫?骞惰鎵ц
   鈹斺攢鈫?鏈変緷璧栫殑浠诲姟 鈫?鎷撴墤鎺掑簭鍚庝覆琛屾墽琛?
7. 缁撴灉楠屾敹
   鈹斺攢鈫?妫€鏌ユ瘡涓瓙Agent杈撳嚭鏄惁婊¤冻楠屾敹鏍囧噯

8. 鏁村悎杈撳嚭
   鈹斺攢鈫?鍚堝苟缁撴灉锛岀敓鎴愮粰鐢ㄦ埛鐨勪竴鑷淬€佸畬鏁村洖澶?```

### 7.2 鍏稿瀷鍦烘櫙绀轰緥

#### 鍦烘櫙A锛氬紑鍙戞柊鍔熻兘
```yaml
鐢ㄦ埛: "缁檖arser妯″潡鍔燡SON瀵煎嚭鍔熻兘"

Orchestrator:
  # Plan 妯″紡: AI 鐢熸垚璁″垝 鈫?TaskAgent 瑙ｆ瀽涓虹粨鏋勫寲浠诲姟
  0. AI 鐢熸垚鎵ц璁″垝 (Plan Mode)
  0a. 鐢ㄦ埛纭 鈫?processConfirmedPlan()
  0b. TaskAgent 瑙ｆ瀽AI鍥炲 鈫?StructuredTask鍒楄〃
      - Phase 1: 璋冪爺 (Explorer, 涓茶)
      - Phase 2: 璁捐 (Architect, 涓茶)
      - Phase 3: 瀹炵幇 (Coder+Tester, 骞跺彂)
      - Phase 4: 瀹℃煡 (Reviewer, 涓茶)
      - Phase 5: GAN杩唬 (Generator鈬凟valuator, 涓茶, 鏈€澶?杞?
  0c. WebSocket 骞挎挱 鈫?鍓嶇"缁撴瀯鍖?瑙嗗浘灞曠ず
  # Act 妯″紡: 浣跨敤 TaskExecutionAgent 閫愭鎵ц
  1. TaskExecutionAgent 鎸夐樁娈甸『搴忔墽琛?  2. Phase 3 骞跺彂: Coder + Tester 绾跨▼姹犲苟琛?  3. 绛?3 瀹屾垚鍚?鈫?Reviewer 瀹℃煡
  4. Phase 5: GAN杩唬寰幆:
     - Generator 鏍规嵁鍙嶉淇敼
     - Evaluator 4缁村姞鏉冭瘎鍒?     - 鏈€氳繃鍒欐敞鍏ュ弽棣堢户缁惊鐜?     - 閫氳繃鎴栬揪鏈€澶ц疆鏁板悗缁堟
  5. 姣忎釜浠诲姟瀹屾垚鍚?鈫?MemoryAgent 鑷姩璁板繂
  6. 鏁村悎鎵€鏈夌粨鏋滐紝姹囨姤缁欑敤鎴?```

#### 鍦烘櫙F锛氳法浠诲姟璁板繂
```yaml
鐢ㄦ埛: "鍒嗘瀽鎵€鏈塉ava鏂囦欢鐨勪緷璧栧叧绯?

Orchestrator:
  # 棣栨鎵ц
  1. 娲?Explorer 鍒嗘瀽鏁翠釜椤圭洰缁撴瀯
  2. 浠诲姟瀹屾垚鍚?鈫?MemoryAgent 璁板綍:
     - 椤圭洰绫诲瀷: maven
     - 璇█: Java, TypeScript
     - 鍏抽敭妯″潡: jwcode-core, jwcode-web
     - 娲炲療: "鎵€鏈夋ā鍧楅€氳繃 pom.xml 鐖跺瓙鍏崇郴绠＄悊"
  3. regeneratePlanContext() 鈫?.jwcode/memory/plan_context.md

  # 涓嬩竴娆′换鍔?  鐢ㄦ埛: "閲嶆瀯 jwcode-core 鐨?agent 鍖?
  4. Plan 妯″紡 鈫?MemoryAgent 鑷姩娉ㄥ叆:
     "宸茬煡妯″潡缁撴瀯銆佷緷璧栧叧绯汇€佺紪鐮佽鑼?.."
  5. AI 鍩轰簬璁板繂鍋氬嚭鏇寸簿鍑嗙殑浠诲姟瑙勫垝
```

#### 鍦烘櫙B锛氫慨澶岯ug
```yaml
鐢ㄦ埛: "淇鐧诲綍妯″潡鐨凬PE闂"

Orchestrator:
  1. 娲?DebugAgent 澶嶇幇闂銆佸畾浣嶆牴鍥?  2. 绛?1 瀹屾垚鍚庯紝娲?CoderAgent 瀹炴柦淇
  3. 绛?2 瀹屾垚鍚庯紝骞惰锛?     - 娲?Tester 缂栧啓鍥炲綊娴嬭瘯骞堕獙璇?     - 娲?Reviewer 瀹℃煡淇鏂规
  4. 鏁村悎缁撴灉锛屾眹鎶ヤ慨澶嶈鎯?```

#### 鍦烘櫙C锛氶噸鏋勪唬鐮?```yaml
鐢ㄦ埛: "閲嶆瀯auth妯″潡锛屾彁鍙栧叕鍏遍€昏緫"

Orchestrator:
  1. 娲?ExploreAgent 鍒嗘瀽 auth 妯″潡鐜扮姸
  2. 娲?ArchitectAgent 鍒跺畾閲嶆瀯璁″垝
  3. 绛?1銆? 瀹屾垚鍚庯紝娲?CoderAgent 鎸夋楠ゆ墽琛岄噸鏋?  4. 绛?3 瀹屾垚鍚庯紝骞惰锛?     - 娲?Tester 杩愯鍏ㄩ噺娴嬭瘯楠岃瘉
     - 娲?Reviewer 瀹℃煡閲嶆瀯璐ㄩ噺
  5. 鏁村悎缁撴灉
```

#### 鍦烘櫙D锛氫唬鐮佸鏌?```yaml
鐢ㄦ埛: "瀹℃煡杩欎釜PR鐨勪唬鐮?

Orchestrator:
  1. 娲?ExploreAgent 鑾峰彇PR鍙樻洿鑼冨洿鍜岀浉鍏充笂涓嬫枃
  2. 绛?1 瀹屾垚鍚庯紝娲?Reviewer 鎵ц璇︾粏瀹℃煡
  3. 濡?Reviewer 鍙戠幇涓ラ噸闂锛屾淳 DebugAgent 楠岃瘉
  4. 鏁村悎瀹℃煡鎶ュ憡
```

#### 鍦烘櫙E锛氱紪鍐欐枃妗?```yaml
鐢ㄦ埛: "鏇存柊API鏂囨。锛屽弽鏄爒2鎺ュ彛鍙樺寲"

Orchestrator:
  1. 娲?ExploreAgent 鎵弿v2鎺ュ彛瀹氫箟
  2. 绛?1 瀹屾垚鍚庯紝娲?Documenter 缂栧啓鏂囨。
  3. 绛?2 瀹屾垚鍚庯紝娲?Reviewer 妫€鏌ユ枃妗ｅ噯纭€?  4. 鏁村悎杈撳嚭
```

---

## 8. 涓婁笅鏂囦紶閫掕鑼?
### 8.1 鏈€灏忓繀瑕佸師鍒?Orchestrator 鍙悜瀛怉gent浼犻€掑畬鎴愪换鍔℃墍闇€鐨勬渶灏忎笂涓嬫枃锛?- **浠ｇ爜浠诲姟**锛氱浉鍏虫枃浠惰矾寰?+ 鎺ュ彛濂戠害 + 绾︽潫鏉′欢
- **娴嬭瘯浠诲姟**锛氳娴嬩唬鐮佽矾寰?+ 宸叉湁娴嬭瘯鍙傝€?+ 瑕嗙洊鐜囪姹?- **鏂囨。浠诲姟**锛氫唬鐮佸彉鏇存憳瑕?+ 鐩爣璇昏€?+ 鏍煎紡瑙勮寖
- **璋冪爺浠诲姟**锛氳皟鐮旇寖鍥?+ 鍏虫敞缁村害 + 杈撳嚭鏍煎紡

### 8.2 寮曠敤鑰岄潪澶嶅埗
- 澶ф枃浠朵紶璺緞锛岃瀛怉gent鑷繁璇诲彇
- 浠ｇ爜鐗囨鍙紶鍏抽敭閮ㄥ垎锛?50琛岋級
- 绂佹鎶婃暣涓唬鐮佸簱涓婁笅鏂囧缁欏瓙Agent

### 8.3 鎴愭灉浼犻€?- 涓婃父浠诲姟杈撳嚭浣滀负涓嬫父浠诲姟鐨?`context` 娉ㄥ叆
- 浣跨敤 SharedContextBus 鍏变韩涓棿鎴愭灉

### 8.4 涓婁笅鏂囧帇缂?- Token浣跨敤鐜囪秴杩?0%鏃惰嚜鍔ㄨЕ鍙?AICL Priority-LRU 娣樻卑
- 瀛愪换鍔″畬鎴愭椂鑷姩娓呯悊涓婁笅鏂?- 鏀寔鎵嬪姩 `/compact` 鍛戒护瑙﹀彂婵€杩涘帇缂?- 姣忚疆瀵硅瘽缁撴潫鑷姩瑙﹀彂 TTL 琛板噺鍜屼唬闄呮鏌?
### 8.5 AICL 涓婁笅鏂囧崗璁紙v1.1锛?- 鎵€鏈変笂涓嬫枃鍧楃粺涓€鍖呰涓?`<ctx:block>` 鍏冪礌锛屾惡甯?`priority`銆乣state`銆乣ttl` 绛夌敓鍛藉懆鏈熷睘鎬?- Assembler 缁勮鏃惰嚜鍔ㄦ墽琛屽垎绾ф窐姹帮紝纭繚 Token 棰勭畻鍐呬俊鎭瘑搴︽渶澶у寲
- AI 閫氳繃娉ㄥ叆鐨?AICL 瑙ｆ瀽瑙勫垯璇嗗埆浼樺厛绾т笌鐘舵€侊紝璋冩暣闃呰绛栫暐
- 瀹屾暣瑙勮寖瑙?`docs/AICL_SPEC.md`

---

## 9. 璐ㄩ噺涓庣害鏉?
### 9.1 Orchestrator 绾㈢嚎锛堢姝㈣涓猴級
- 鉂?鐩存帴璋冪敤 `FileReadTool` / `FileWriteTool` / `FileEditTool`
- 鉂?鐩存帴璋冪敤 `BashTool` / `PowerShellTool` / `REPLTool`
- 鉂?鐩存帴璋冪敤 `GlobTool` / `GrepTool` 鎼滅储浠ｇ爜搴?- 鉂?缂栧啓浠讳綍浠ｇ爜銆佷慨鏀逛换浣曢厤缃?- 鉂?瓒婅繃 `AgentTool` 鐩存帴"鑷繁鍔ㄦ墜"

### 9.2 瀛怉gent 绾︽潫
- **Reviewer / Explorer**锛氬彧璇绘ā寮忥紝绂佹淇敼浠讳綍鏂囦欢
- **Coder / Tester / Doc / Architect**锛氬彲璇诲啓鏂囦欢锛屼絾闇€鍦ㄨ亴璐ｈ寖鍥村唴
- **鎵€鏈夊瓙Agent**锛氫笉鍙啀鍒涘缓瀛怉gent锛堥槻姝㈤€掑綊锛夛紝AgentTool 鍦ㄥ瓙Agent鎵ц鏃跺凡琚帓闄?
### 9.3 璐ㄩ噺妫€鏌ユ竻鍗?Orchestrator 鍦ㄦ暣鍚堣緭鍑哄墠蹇呴』纭锛?- [ ] 鎵€鏈夊瓙浠诲姟宸插畬鎴愶紙鎴栧凡璁板綍澶辫触鍘熷洜锛?- [ ] 浠ｇ爜绫讳换鍔℃湁瀵瑰簲鐨?review 鎴?test 缁撴灉
- [ ] 鏂囨。绫讳换鍔′笌浠ｇ爜鍙樻洿淇濇寔涓€鑷?- [ ] 鏃犻仐婕忕殑杈圭晫鎯呭喌
- [ ] 杈撳嚭鏍煎紡缁熶竴銆佹棤鍐茬獊

---

## 10. 鏁呴殰澶勭悊

### 10.1 瀛怉gent 澶辫触
```
瀛怉gent澶辫触
    鈫?Orchestrator鍒ゆ柇澶辫触绫诲瀷
    鈹溾攢鈹€ 杈撳叆涓嶆竻 鈫?閲嶆柊鍒嗚В浠诲姟锛岃ˉ鍏呬笂涓嬫枃
    鈹溾攢鈹€ 鎶€鏈敊璇?鈫?鎹㈠瓙Agent閲嶈瘯 / 闄嶇骇澶勭悊锛堝Coder澶辫触鎹efault锛?    鈹斺攢鈹€ 鏃犳硶瑙ｅ喅 鈫?鍚戠敤鎴锋眹鎶ュけ璐ュ師鍥犲拰寤鸿
```

### 10.2 杩囧害鎷嗚В淇濇姢
- 绠€鍗曚换鍔★紙濡傛敼涓€涓彉閲忓悕锛夌洿鎺ユ寚娲剧粰鍗曚釜瀛怉gent锛屼笉瑕佹媶鎴愬姝?- 棰勪及瀛愪换鍔℃墽琛屾椂闂?< 30绉掔殑锛屽悎骞跺埌鍚屼釜瀛怉gent涓?
### 10.3 寰幆渚濊禆妫€娴?- 浠诲姟渚濊禆鍥惧繀椤绘槸 DAG锛堟湁鍚戞棤鐜浘锛?- Orchestrator 鍦ㄦ媶瑙ｆ椂妫€娴嬪苟鎵撶牬寰幆

---

## 11. 閰嶇疆

### 11.1 鍥㈤槦閰嶇疆
鍥㈤槦閰嶇疆浣嶄簬 `.jwcode/team_members.json`锛屽畾涔変簡锛?- 鍥㈤槦缁勬垚鍜岃鑹?- Agent鑱岃矗鍜岃兘鍔?- 宸ヤ綔娴佽鍒欙紙骞惰搴︺€佸鎵圭瓥鐣ャ€佸崌绾х瓥鐣ワ級

### 11.2 Agent娉ㄥ唽
Agent娉ㄥ唽浣嶄簬 `AgentRegistry.java`锛屾墍鏈堿gent鍦ㄧ郴缁熷惎鍔ㄦ椂鑷姩娉ㄥ唽銆?
**鍐呴儴鏈嶅姟Agent**锛圱askAgent / TaskExecutionAgent / MemoryAgent锛夌敱 `EnhancedOrchestratorAgent` 
鐩存帴瀹炰緥鍖栧拰璋冪敤锛屼笉闇€瑕侀€氳繃 AgentRegistry 娉ㄥ唽锛屼篃涓嶆毚闇茬粰澶栭儴 A2A 鍗忚銆?MemoryAgent 鎸夊伐浣滅洰褰曞疄渚嬪寲锛堟瘡涓」鐩竴涓疄渚嬶級锛屾暟鎹瓨鍌ㄥ湪 `.jwcode/memory/` 涓嬨€?
---

## 12. 鎵╁睍鎸囧崡

### 12.1 娣诲姞鏂板瓙Agent
1. 瀹炵幇 `Agent` 鎺ュ彛
2. 瀹氫箟绯荤粺鎻愮ず璇嶏紙鏄庣‘鑱岃矗杈圭晫锛?3. 閰嶇疆鍙敤宸ュ叿鐧藉悕鍗?4. 鍦?`AgentRegistry.registerDefaultAgents()` 涓敞鍐?5. 鏇存柊 `.jwcode/team_members.json`

### 12.2 淇敼 Orchestrator 绛栫暐
Orchestrator 鐨勬媶瑙ｅ拰璋冨害閫昏緫鍙€氳繃浠ヤ笅鏂瑰紡鎵╁睍锛?- 淇敼 `TaskPlanner` 涓殑瑙勫垯妯℃澘
- 璋冩暣 `SubTaskSplitter` 鐨勫惎鍙戝紡绛栫暐
- 鑷畾涔?`ParallelAgentExecutor` 鐨勪緷璧栧浘绠楁硶

---

## 13. 鏂囨。绱㈠紩

| 鏂囨。 | 璇存槑 |
|------|------|
| `AGENTS.md` | AI 鍗忎綔瑙勮寖 + Agent 鏋舵瀯 (鏈枃妗? |
| `README.md` | 椤圭洰姒傝堪 (鑷姩鐢熸垚) |
| `docs/ARCHITECTURE_V2.md` | 鏋舵瀯婕旇繘璁板綍 (v1鈫抳2鈫抳3鈫抳3.1) |
| `docs/JWCODE_PRODUCT_DESIGN.md` | 浜у搧璁捐鏂囨。 |
| `docs/AICL_SPEC.md` | AICL 涓婁笅鏂囧崗璁鑼?|
| `docs/CONFIG_GUIDE.md` | 閰嶇疆鎸囧崡 |
| `docs/HOOK_SYSTEM_GUIDE.md` | Hook 鐢熷懡鍛ㄦ湡鎷︽埅浣撶郴 |
| `docs/developer-guide.md` | 寮€鍙戣€呮枃妗?|
| `docs/agent-bridge-guide.md` | Agent 妗ユ帴妯″紡鎸囧崡 |
## 14. 鐩稿叧鏂囦欢

| 鏂囦欢 | 璇存槑 |
|------|------|
| `jwcode-core/src/main/java/com/jwcode/core/agent/OrchestratorAgent.java` | 涓籄gent瀹炵幇 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/EnhancedOrchestratorAgent.java` | 澧炲己鍨嬩富Agent锛圥DCA寰幆锛岄泦鎴怲askAgent+TaskExecutionAgent锛?|
| `jwcode-core/src/main/java/com/jwcode/core/agent/CoderAgent.java` | 浠ｇ爜涓撳 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/DebugAgent.java` | 璋冭瘯涓撳 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/ReviewerAgent.java` | 瀹℃煡涓撳 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/TestAgent.java` | 娴嬭瘯涓撳 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/DocAgent.java` | 鏂囨。涓撳 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/ExploreAgent.java` | 鎺㈢储涓撳 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/ArchitectAgent.java` | 鏋舵瀯涓撳 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/TaskAgent.java` | 浠诲姟缁撴瀯鍖朅gent锛圓I鍥炲鈫掔粨鏋勫寲浠诲姟鍒楄〃锛?|
| `jwcode-core/src/main/java/com/jwcode/core/agent/TaskExecutionAgent.java` | 浠诲姟鎵цAgent锛堝苟鍙?涓茶閫愭璋冨害瀛怉gent锛?|
| `jwcode-core/src/main/java/com/jwcode/core/agent/MemoryAgent.java` | 宸ヤ綔鐩綍璁板繂Agent锛堥」鐩ā寮?娲炲療/鍋忓ソ涓诲姩璁板繂锛?|
| `jwcode-core/src/main/java/com/jwcode/core/agent/WorkspaceMemoryStore.java` | 宸ヤ綔鐩綍璁板繂鎸佷箙鍖栧瓨鍌紙.jwcode/memory/锛?|
| `jwcode-core/src/main/java/com/jwcode/core/agent/CompactorAgent.java` | 涓婁笅鏂囧帇缂╀笓瀹?|
| `jwcode-core/src/main/java/com/jwcode/core/agent/CompactorTrigger.java` | 鍘嬬缉瑙﹀彂绛栫暐 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/EvaluatorAgent.java` | GAN寮忚瘎浼颁笓瀹讹紙4缁村姞鏉冭瘎鍒?纭棬妲涘惁鍐筹級 |
| `jwcode-core/src/main/java/com/jwcode/core/service/StructuredCompactionStrategy.java` | 寮哄埗XML缁撴瀯鍖栧帇缂╃瓥鐣ワ紙浼樺厛绾ф埅鏂級 |
| `jwcode-core/src/main/java/com/jwcode/core/service/IterativeSprintOrchestrator.java` | GAN杩唬寰幆浠茶鍣紙Generator鈬凟valuator鍙嶉闂幆锛?|
| `jwcode-core/src/main/java/com/jwcode/core/service/ContextResetManager.java` | 涓婁笅鏂囬噸缃鐞嗗櫒锛堣繘绋嬬骇閲嶅惎+缁撴瀯鍖栦氦鎺ワ級 |
| `jwcode-core/src/main/java/com/jwcode/core/` | AICL 6绾т紭鍏堢骇鏋氫妇 |
| `jwcode-core/src/main/java/com/jwcode/core/` | AICL 6鐘舵€佺敓鍛藉懆鏈熸灇涓?|
| `jwcode-core/src/main/java/com/jwcode/core/` | AICL 涓婁笅鏂囧潡妯″瀷 |
| `jwcode-core/src/main/java/com/jwcode/core/` | AICL 鎺у埗灞傦紙棰勭畻+绛栫暐锛?|
| `jwcode-core/src/main/java/com/jwcode/core/` | AICL Priority-LRU 娣樻卑寮曟搸 |
| `jwcode-core/src/main/java/com/jwcode/core/` | AICL XML 搴忓垪鍖栧櫒 |
| `jwcode-core/src/main/java/com/jwcode/core/` | AICL XML 鍙嶅簭鍒楀寲鍣?|
| `jwcode-core/src/main/java/com/jwcode/core/` | AICL 瀹屾暣涓婁笅鏂囧鍣?|
| `jwcode-core/src/main/java/com/jwcode/core/` | AICL AI瑙ｆ瀽瑙勫垯Prompt鐢熸垚鍣?|
| `jwcode-core/src/main/java/com/jwcode/core/agent/AgentRegistry.java` | Agent娉ㄥ唽琛?|
| `jwcode-core/src/main/java/com/jwcode/core/model/StructuredTask.java` | 缁撴瀯鍖栦换鍔℃ā鍨嬶紙鎵ц妯″紡+闃舵+骞跺彂缁?contractId锛?|
| `jwcode-core/src/main/java/com/jwcode/core/model/SprintContract.java` | Sprint鍚堝悓妯″瀷锛圖RAFT鈫扤EGOTIATING鈫扴IGNED鈫扙XECUTING锛?|
| `jwcode-core/src/main/java/com/jwcode/core/model/EvaluationScore.java` | 4缁村姞鏉冭瘎鍒嗘ā鍨嬶紙浜у搧娣卞害/鍔熻兘/瑙嗚/浠ｇ爜璐ㄩ噺锛?|
| `jwcode-core/src/main/java/com/jwcode/core/model/EvaluationReport.java` | 璇勪及鎶ュ憡妯″瀷锛堝惈verdict+闂ㄦ妫€鏌?鍙嶉鎽樿锛?|
| `jwcode-core/src/main/java/com/jwcode/core/model/HandoffArtifact.java` | 浜ゆ帴鏂囨。妯″瀷锛圕ontext Reset鍗忚鏍稿績锛?|
| `jwcode-core/src/main/java/com/jwcode/core/tool/ToolAgent.java` | 宸ュ叿鎵цAgent锛堢3灞傦級 |
| `jwcode-core/src/main/java/com/jwcode/core/tool/ToolAgentResult.java` | 宸ュ叿鎵ц缁撴灉 |
| `jwcode-core/src/main/java/com/jwcode/core/a2a/model/ErrorSummary.java` | 閿欒鎽樿妯″瀷 |
| `jwcode-core/src/main/java/com/jwcode/core/a2a/model/StepStatus.java` | 姝ラ鐘舵€佹灇涓?|
| `jwcode-core/src/main/java/com/jwcode/core/a2a/model/TaskLifecycle.java` | 浠诲姟鐢熷懡鍛ㄦ湡 |
| `jwcode-core/src/main/java/com/jwcode/core/a2a/model/RetryPolicy.java` | 閲嶈瘯绛栫暐閰嶇疆 |
| `jwcode-core/src/main/java/com/jwcode/core/a2a/retry/RetryStrategy.java` | 閲嶈瘯绛栫暐绠楁硶 |
| `jwcode-core/src/main/java/com/jwcode/core/a2a/retry/RetryOrchestrator.java` | 鍒嗗眰閲嶈瘯缂栨帓鍣?|
| `jwcode-core/src/main/java/com/jwcode/core/hook/HookDecision.java` | Hook鍐崇瓥璇箟鏋氫妇锛坴2.1锛?|
| `jwcode-core/src/main/java/com/jwcode/core/hook/HookEventType.java` | Hook浜嬩欢绫诲瀷鏋氫妇锛坴2.1锛?|
| `jwcode-core/src/main/java/com/jwcode/core/hook/HookChain.java` | Hook鎷︽埅閾剧紪鎺掑紩鎿庯紙v2.1锛?|
| `jwcode-core/src/main/java/com/jwcode/core/hook/HookRegistry.java` | Hook閰嶇疆鍔犺浇涓庣儹閲嶈浇锛坴2.1锛?|
| `jwcode-core/src/main/java/com/jwcode/core/hook/HookExecutor.java` | Hook鎵ц鍣ㄦ帴鍙ｏ紙v2.1锛?|
| `jwcode-core/src/main/java/com/jwcode/core/hook/executor/ShellHookExecutor.java` | Shell鑴氭湰Hook锛坴2.1锛?|
| `jwcode-core/src/main/java/com/jwcode/core/hook/executor/HttpHookExecutor.java` | HTTP绔偣Hook锛坴2.1锛?|
| `jwcode-core/src/main/java/com/jwcode/core/hook/executor/PromptHookExecutor.java` | LLM Prompt Hook锛坴2.1锛?|
| `jwcode-core/src/main/java/com/jwcode/core/hook/executor/AgentHookExecutor.java` | Agent璋冩煡Hook锛坴2.1锛?|
| `jwcode-core/src/main/java/com/jwcode/core/hook/HookAuditLogger.java` | Hook瀹¤鏃ュ織锛坴2.1锛?|
| `jwcode-core/src/main/java/com/jwcode/core/hook/RollbackAction.java` | 鍥為€€绛栫暐鏋氫妇锛坴2.1锛?|
| `jwcode-core/src/main/java/com/jwcode/core/planner/TaskPlanner.java` | 浠诲姟瑙勫垝鍣?|
| `jwcode-core/src/main/java/com/jwcode/core/agent/SubTaskSplitter.java` | 瀛愪换鍔℃媶鍒嗗櫒 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/parallel/ParallelAgentExecutor.java` | 骞惰鎵ц鍣?|
| `jwcode-core/src/main/java/com/jwcode/core/planner/checkpoint/CheckpointManager.java` | 妫€鏌ョ偣绠＄悊鍣?|
| `jwcode-core/src/main/java/com/jwcode/core/planner/checkpoint/SharedContextBus.java` | 鍏变韩涓婁笅鏂囨€荤嚎 |
| `jwcode-web/src/components/Plan/StructuredTaskView.tsx` | 鍓嶇缁撴瀯鍖栦换鍔¤鍥剧粍浠?|
| `jwcode-web/src/stores/planStore.ts` | 鍓嶇 Plan 鐘舵€佺鐞嗭紙鍚粨鏋勫寲浠诲姟锛?|
| `jwcode-core/src/test/java/com/jwcode/core/a2a/FourLayerIntegrationTest.java` | 鍥涘眰鏋舵瀯闆嗘垚娴嬭瘯锛?5涓祴璇曠敤渚嬶級 |
| `.jwcode/team_members.json` | 鍥㈤槦閰嶇疆 |

---

## 15. Harness Engineering锛坴3.0 鏂板 / 妯悜鍩虹璁炬柦锛?
Harness Engineering 鏄?Agent 鐨?缂扮怀"浣撶郴锛岀‘淇?AI 浠?閲庨┈"鍙樹负"鍗冮噷椹?銆?鍥涘眰閫掕繘锛歀1 瀹夊叏 鈫?L2 鎴愭湰 鈫?L3 璐ㄩ噺 鈫?L4 鍙娴嬨€?
### L1: 娌欑瀹夊叏

| 缁勪欢 | 鏂囦欢 | 鑱岃矗 |
|------|------|------|
| `DockerSandboxExecutor` | `tool/shell/DockerSandboxExecutor.java` | Docker 瀹瑰櫒闅旂鎵ц锛坄--network=none --memory=512m :ro`锛?|
| `WorkspaceGuard` | `tool/WorkspaceGuard.java` | 鏂囦欢绯荤粺杈圭晫鏍￠獙锛圱OCTOU 闃叉姢锛?|
| `HookAuditLogger` | `hook/HookAuditLogger.java` | 鎿嶄綔瀹¤鏃ュ織 |
| `PermissionManager` | `permission/PermissionManager.java` | 浜旂骇鏉冮檺鎺у埗 |

### L2: 鎴愭湰涓庤矾鐢?
| 缁勪欢 | 鏂囦欢 | 鑱岃矗 |
|------|------|------|
| `ModelRouter` | `llm/ModelRouter.java` | 鎸変换鍔＄壒寰佸姩鎬侀€夋嫨妯″瀷锛堝璇濃啋杞婚噺 / 鎺ㄧ悊鈫掓棗鑸帮級 |
| `CostTrackerService` | `service/CostTrackerService.java` | 瀹炴椂鎴愭湰杩借釜锛堝凡鎺ュ叆 LLMQueryEngine锛?|
| `TokenBudget` | `llm/TokenBudget.java` | Token 棰勭畻绠＄悊 |
| `ContextWindowManager.zonePriority()` | `service/ContextWindowManager.java` | 浜旂骇 ZONE 鎴柇浼樺厛绾?|

### L3: 璐ㄩ噺涓庤蹇?
| 缁勪欢 | 鏂囦欢 | 鑱岃矗 |
|------|------|------|
| `RecoveryExecutor` (AiRepair) | `resilience/RecoveryExecutor.java` | LLM 鍒嗘瀽閿欒鈫掔敓鎴愪慨澶嶆柟妗堚啋閲嶈瘯 |
| `RecoveryProtocol.AiRepair` | `resilience/RecoveryProtocol.java` | 涓夐樁娈垫仮澶嶅崗璁紙AutoRetry鈫扐iRepair鈫扝umanEscalation锛?|
| `SimpleCompactionStrategy` (浜旂骇鍒嗗眰) | `service/SimpleCompactionStrategy.java` | 閿欒/鏂囦欢淇敼/璇诲彇/鍛戒护 鍒嗗眰淇濈暀 |
| `WorkspaceMemoryStore.semanticSearch()` | `agent/WorkspaceMemoryStore.java` | Embedding 璇箟妫€绱?+ 鍏抽敭璇嶉檷绾?|
| `LLMService.embed()` | `llm/LLMService.java` | 鏂囨湰宓屽叆鎺ュ彛 |
| Prompt Caching | `llm/LLMMessage.java` (CacheControl) | `cache_control: ephemeral` 鍑忓皯 ~70% 閲嶅 token |

### L4: 鍙娴?
| 缁勪欢 | 鏂囦欢 | 鑱岃矗 |
|------|------|------|
| `ObservationPipeline` | `observability/ObservationPipeline.java` | 浜嬩欢绠￠亾锛?2 绉嶄簨浠剁被鍨嬶級 |
| `AnalyticsObserver` | `observability/AnalyticsObserver.java` | 鑱氬悎缁熻 |

### 瀹夊叏鎵ц绠￠亾

```
ToolExecutor.execute()
  鈫?PermissionManager.isDestructive()     // 鏉冮檺
  鈫?WorkspaceGuard.validatePath()         // 璺緞
  鈫?HookChain.execute(PRE_TOOL_USE)       // Hook
  鈫?DockerSandboxExecutor.execute()       // 娌欑
  鈫?HookChain.execute(POST_TOOL_USE)      // 瀹¤
  鈫?HookAuditLogger.record()              // 鏃ュ織
```

