# RCA & Evidence State Model Prototype Split Package

本压缩包将 RCA / Evidence 状态模型原型图拆成单页面图片，并为每个页面状态提供 Markdown spec。

## 使用原则

```text
1. Hermes 以 specs/*.md 为准
2. images/*.png 只作为视觉辅助参考
3. 如果图片与 spec 冲突，以 spec 为准
4. 不要求 Hermes 直接理解大拼图
5. 每次实现只选择一个页面 / 一个状态 / 一个阶段
```

## 文件结构

```text
images/
  01_rca_runs_list_main_entry.png
  02_rca_detail_completed_with_evidence.png
  03_rca_detail_completed_no_evidence.png
  04_rca_detail_not_started.png
  05_rca_detail_running.png
  06_evidence_select_rca_run_empty.png

specs/
  01_rca_runs_list_main_entry.md
  02_rca_detail_completed_with_evidence.md
  03_rca_detail_completed_no_evidence.md
  04_rca_detail_not_started.md
  05_rca_detail_running.md
  06_evidence_select_rca_run_empty.md
```

## 给 Hermes 的推荐说明

```text
原型图只作为视觉参考，不作为唯一需求来源。
请优先遵循 Markdown spec 中的页面结构、状态模型、字段定义和验收标准。
如果原型图与文字需求冲突，以文字需求为准。
不要基于原型图自行扩展功能。
如果无法识别原型图中的文字或布局，不要猜测；按 spec 实现。
```

## 原图信息

原图尺寸：1536 x 1024
拆分页面 / 状态数：6
