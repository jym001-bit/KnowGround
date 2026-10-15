# 来源与交付边界

本项目基于用户提供的 `watercolor-components.zip` 整理。原包说明其绘图代码来自 Anthropic Economic Scenarios 公开脚本的恢复与提取。本次保留绘图算法，并接入 Vue 组件、独立滤镜命名空间与参数约束。

原包的 package.json 标注 MIT，但没有附带 LICENSE 或字体授权文件，因此当前工作包设为 private，未声明新的开源授权。这里不把该标注视为已经核实的上游授权。

原有 Anthropic 字体保留在本地输入目录，默认组件样式使用系统字体；生成的 npm 安装包不包含这些字体。组件库没有发布到任何公共 registry。
