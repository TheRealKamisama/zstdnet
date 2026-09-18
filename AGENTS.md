# 仓库协作规则

## Git 提交署名

- Codex 参与完成的代码或文档，每次提交都必须在提交说明末尾添加以下 Git trailer，并与正文之间保留一个空行：

  ```text
  Co-authored-by: Codex <noreply@openai.com>
  ```

- 保留用户作为主要作者，不要用 Codex 替换用户的 author 身份。
- 保留其他参与者已有的 `Co-authored-by`，不要重复添加相同署名。
- 执行 commit 前检查提交说明是否包含该署名；合并或 squash 时也应保留。
