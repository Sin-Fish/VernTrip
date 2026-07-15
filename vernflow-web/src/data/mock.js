function workspaceItem(path, label, kind) {
  return { path, label, kind };
}

export const pageMeta = {
  home: {
    title: "主页",
    description: "从资料导入、任务执行到结果审查的统一入口。",
  },
  library: {
    title: "资料库",
    description: "浏览、整理、上传和管理通用工作区文件。",
  },
  workbench: {
    title: "工作台",
    description: "查看任务记录，并进入文脉图执行详情。",
  },
  assistant: {
    title: "文档助手",
    description: "基于当前范围提问，并查看引用证据。",
  },
  settings: {
    title: "设置",
    description: "管理智能体参数、能力库与检索检查策略。",
  },
};

export const navigationGroups = [
  {
    label: "主功能",
    items: [
      { label: "主页", page: "home" },
      { label: "资料库", page: "library" },
      { label: "工作台", page: "workbench" },
      { label: "文档助手", page: "assistant" },
    ],
  },
  {
    label: "输出",
    items: [
      { label: "生成结果" },
      { label: "审查记录" },
    ],
  },
  {
    label: "系统",
    items: [
      { label: "设置", page: "settings" },
    ],
  },
];

const treeItems = [
  workspaceItem("workspace://project-materials", "项目资料", "folder"),
  workspaceItem("workspace://system-design", "系统设计", "folder"),
  workspaceItem("workspace://references", "参考资料", "folder"),
  workspaceItem("workspace://media", "媒体素材", "folder"),
  workspaceItem("workspace://project-brief.md", "项目简报.md", "file"),
];

const workbenchRecords = [
  {
    name: "资料整理任务",
    skills: "检索 / 整理 / 审查",
    status: "运行中",
    statusTone: "run",
    inspectMode: "重点",
    time: "今天",
  },
  {
    name: "工作区结构梳理",
    skills: "目录整理 / 文件操作",
    status: "已完成",
    statusTone: "done",
    inspectMode: "关闭",
    time: "昨天",
  },
  {
    name: "引用证据检查",
    skills: "断言检查 / 证据匹配",
    status: "待处理",
    statusTone: "warn",
    inspectMode: "完整",
    time: "昨天",
  },
];

const workbenchNodes = [
  {
    id: "scope",
    type: "范围",
    title: "读取工作范围",
    status: "完成",
    tone: "done",
    detail: "2 份资料",
    positionClass: "n1",
    checks: [
      { tone: "on", label: "输入范围明确", result: "正常" },
      { tone: "on", label: "工作文件可解析", result: "正常" },
      { tone: "warn", label: "是否需要扩展到全局工作区", result: "待确认" },
    ],
  },
  {
    id: "retriever",
    type: "检索",
    title: "本地召回",
    status: "完成",
    tone: "done",
    detail: "Top 24",
    positionClass: "n2",
    checks: [
      { tone: "on", label: "召回结果可追溯", result: "正常" },
      { tone: "on", label: "命中文件覆盖当前主题", result: "正常" },
      { tone: "warn", label: "边缘资料是否遗漏", result: "待确认" },
    ],
  },
  {
    id: "branch",
    type: "分支",
    title: "网页补充",
    status: "跳过",
    tone: "wait",
    detail: "补充分支",
    positionClass: "branch",
    checks: [
      { tone: "on", label: "输入范围明确", result: "正常" },
      { tone: "on", label: "支线已回并主路径", result: "正常" },
      { tone: "warn", label: "网页补充来源可信度", result: "待确认" },
    ],
  },
  {
    id: "selector",
    type: "筛选",
    title: "证据覆盖选择",
    status: "完成",
    tone: "done",
    detail: "覆盖优先",
    positionClass: "n3",
    checks: [
      { tone: "on", label: "候选证据保留出处", result: "正常" },
      { tone: "on", label: "覆盖策略可解释", result: "正常" },
      { tone: "warn", label: "是否需要改用 MMR", result: "待确认" },
    ],
  },
  {
    id: "writer",
    type: "整理",
    title: "整理资料",
    status: "运行中",
    tone: "run",
    detail: "工作区输出",
    positionClass: "n4",
    checks: [
      { tone: "on", label: "输出结构已绑定工作文件", result: "正常" },
      { tone: "on", label: "整理建议可落地", result: "正常" },
      { tone: "warn", label: "目录调整是否影响旧引用", result: "待确认" },
    ],
  },
  {
    id: "audit",
    type: "审查",
    title: "结果检查",
    status: "排队中",
    tone: "wait",
    detail: "重点审查",
    positionClass: "n5",
    checks: [
      { tone: "on", label: "检查链路已接入", result: "正常" },
      { tone: "on", label: "断言检查规则已就绪", result: "正常" },
      { tone: "warn", label: "实验结果来源待补充", result: "待确认" },
    ],
  },
];

const graphLinks = [
  { from: "scope", to: "retriever", dashed: false },
  { from: "retriever", to: "selector", dashed: false },
  { from: "retriever", to: "branch", dashed: true },
  { from: "branch", to: "selector", dashed: false },
  { from: "selector", to: "writer", dashed: false },
  { from: "writer", to: "audit", dashed: false },
];

const graphMainPath = ["scope", "retriever", "selector", "writer", "audit"];

const chatSessions = [
  {
    id: "session-review",
    title: "资料整理评估",
    summary: "工作文件范围、结构建议与引用线索",
  },
  {
    id: "session-layout",
    title: "前端布局讨论",
    summary: "工作文件、能力与文脉图衔接",
  },
  {
    id: "session-evidence",
    title: "证据覆盖策略",
    summary: "覆盖率与检查节点说明",
  },
];

const chatMessages = [
  {
    id: "m-user-1",
    role: "user",
    text: "前端怎么体现这个卖点？",
    cites: [],
  },
  {
    id: "m-assistant-1",
    role: "assistant",
    text: "当前回答会优先绑定右侧工作文件，并在生成前选择证据片段。切换到全局工作区后，系统才会跨整个资料库继续召回资料。",
    cites: ["工作文件", "范围选择", "能力配置"],
  },
];

const settingsTabs = [
  { id: "basic", label: "基础设置" },
  { id: "agent", label: "智能体设置" },
  { id: "skills", label: "能力库" },
  { id: "advanced", label: "高级设置" },
];

const skillLibraryRows = [
  {
    name: "证据化文档生成",
    summary: "系统内置：检索资料、选择证据、生成文档并执行检查。",
    actions: ["详情", "复制", "停用"],
  },
  {
    name: "资料整理",
    summary: "系统内置：根据目录、类型和摘要生成整理建议。",
    actions: ["详情", "复制", "停用"],
  },
  {
    name: "网页资料补充",
    summary: "系统内置：公开网页搜索、正文读取和外部资料保存。",
    actions: ["详情", "复制", "停用"],
  },
  {
    name: "项目文档生成",
    summary: "我的能力：固定整理结构、导出 docx，并保存审查记录。",
    actions: ["编辑", "复制", "删除"],
  },
];

const skillPermissionSeeds = [
  {
    id: "evidence-doc",
    name: "证据化文档生成",
    summary: "系统内置：已启用",
    open: true,
    agents: [
      { id: "decision", label: "决策智能体", description: "允许用于路径规划", enabled: true },
      { id: "execution", label: "执行智能体", description: "允许执行检索与生成", enabled: true },
      { id: "inspection", label: "审查智能体", description: "允许检查整理结果", enabled: true },
    ],
  },
  {
    id: "organize",
    name: "资料整理",
    summary: "系统内置：已启用",
    open: false,
    agents: [
      { id: "decision", label: "决策智能体", description: "允许生成整理建议", enabled: true },
      { id: "execution", label: "执行智能体", description: "允许执行文件操作", enabled: true },
      { id: "inspection", label: "审查智能体", description: "默认不单独检查", enabled: false },
    ],
  },
  {
    id: "web-fill",
    name: "网页资料补充",
    summary: "系统内置：补充分支",
    open: false,
    agents: [
      { id: "decision", label: "决策智能体", description: "允许作为补充分支", enabled: true },
      { id: "execution", label: "执行智能体", description: "允许读取公开网页", enabled: true },
      { id: "inspection", label: "审查智能体", description: "默认不单独检查", enabled: false },
    ],
  },
];

function cloneSkillPermissions() {
  return skillPermissionSeeds.map((skill) => ({
    ...skill,
    agents: skill.agents.map((agent) => ({ ...agent })),
  }));
}

export function createDefaultWorkspaceBootstrap() {
  return {
    homeSummary: {
      totalFileCount: 6,
      totalFolderCount: 4,
      workingFileCount: 2,
      scopeMode: "working_files",
    },
    pageMeta: {
      ...pageMeta,
    },
    treeItems: treeItems.map((item) => ({ ...item })),
    workingFiles: [
      workspaceItem("workspace://project-brief.md", "项目简报.md", "file"),
      workspaceItem("workspace://project-materials", "项目资料", "folder"),
    ],
    globalScope: false,
    selectedTreePath: treeItems[0].path,
    selectedNodeId: "selector",
    selectedSessionId: chatSessions[0].id,
    homeTaskDraft: "整理当前工作区资料，并给出目录建议。",
    workbenchPrompt: "基于右侧工作文件完成当前资料任务。",
    libraryItems: treeItems.map((item) => ({
      name: item.label,
      kind: item.kind,
      ext: item.kind === "file" ? item.label.split(".").pop() ?? "" : "",
      summary: item.kind === "folder" ? "工作区目录，可继续整理。" : "工作区文件，可加入工作文件。",
      path: item.path,
    })),
    workbenchRecords: workbenchRecords.map((item) => ({ ...item })),
    workbenchNodes: workbenchNodes.map((node) => ({
      ...node,
      checks: node.checks.map((check) => ({ ...check })),
    })),
    graphLinks: graphLinks.map((item) => ({ ...item })),
    graphMainPath: [...graphMainPath],
    workbenchWorkspaceStats: [
      { label: "目录数", value: "4" },
      { label: "文件数", value: "6" },
      { label: "工作文件", value: "2" },
      { label: "当前范围", value: "工作文件" },
    ],
    chatSessions: chatSessions.map((item) => ({ ...item })),
    chatMessages: chatMessages.map((item) => ({
      ...item,
      cites: [...item.cites],
    })),
    settingsTabs: settingsTabs.map((item) => ({ ...item })),
    skillLibraryRows: skillLibraryRows.map((item) => ({
      ...item,
      actions: [...item.actions],
    })),
    skillPermissions: cloneSkillPermissions(),
  };
}
