# Release Notes

## v0.4.0

**性能**

- Skija 绘制路径改用精简的 GL 状态快照，降低每帧状态查询开销。

**配置**

- 可用 `-Datomlib.glscope.slim=false` 回退到完整快照边界。
- 可用 `-Datomlib.glscope.verify=true` 校验被省略的状态族。

----

**Performance**

- The Skija drawing path now uses a slim GL state snapshot, reducing per-frame state queries.

**Config**

- Use `-Datomlib.glscope.slim=false` to fall back to the full snapshot boundary.
- Use `-Datomlib.glscope.verify=true` to verify omitted state families.
