# Changelog

## v0.4.0

**性能**

- 为 Skija 专用绘制路径提供精简的 GL 状态快照，每帧原生状态查询由约 854 次降至约 495 次。

**新增**

- GL 状态快照的 JFR 事件增加原生调用计数与瘦身边界标记。

**配置**

- 新增 `-Datomlib.glscope.slim=false`，回退到完整快照边界。
- 新增 `-Datomlib.glscope.verify=true`，校验被省略的状态族。

----

**Performance**

- Added a slim GL state snapshot for the Skija-only drawing path, cutting native state queries from about 854 to about 495 per frame.

**Added**

- The GL state snapshot JFR event now reports native call counts and the slim boundary flag.

**Config**

- Added `-Datomlib.glscope.slim=false` to fall back to the full snapshot boundary.
- Added `-Datomlib.glscope.verify=true` to verify the omitted state families.
