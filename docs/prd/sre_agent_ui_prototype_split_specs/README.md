# SRE Agent UI Prototype Split Package

本压缩包将原始 UI 大图拆成单页面图片，并为每个页面提供 Markdown spec。

## 使用原则

```text
1. Hermes 以 Markdown spec 为准
2. 图片只作为视觉辅助参考
3. 如果图片与 spec 冲突，以 spec 为准
4. 不要求 Hermes 直接理解大图
5. 每次实现只选择一个页面 / 一个阶段
```

## 文件结构

```text
images/
  01_service_health_overview.png
  02_anomaly_detail_key_metrics.png
  03_rca_analysis_result.png
  04_evidence_drilldown.png
  05_chaos_experiment_config.png

specs/
  01_service_health_overview.md
  02_anomaly_detail_key_metrics.md
  03_rca_analysis_result.md
  04_evidence_drilldown.md
  05_chaos_experiment_config.md
```

## 原型图处理规则

给 Hermes 的提示词建议包含：

```text
原型图只作为视觉参考，不作为唯一需求来源。
请优先遵循 Markdown spec 中的页面结构、状态模型、字段定义和验收标准。
如果原型图与文字需求冲突，以文字需求为准。
不要基于原型图自行扩展功能。
如果无法识别原型图中的文字或布局，不要猜测；按 spec 实现。
```

## 本包生成信息

原图尺寸：1536 x 1024
拆分页面数：5
