package com.opay.offline.component.monitor.util;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitorAdapter;
import com.alibaba.druid.util.JdbcConstants;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class DruidSqlParserHelper {

    /**
     * 解析结果载体
     */
    @Getter
    public static class SqlParamAnalysis {
        // 包含 SET 的值 + WHERE 的值
        private final Map<String, Object> allParams = new LinkedHashMap<>();
        // 仅包含 WHERE / ON 的条件值
        private final Map<String, Object> whereParams = new LinkedHashMap<>();
    }

    /**
     * 全量解析 SQL 中的参数，并区分 Where 条件
     */
    public static SqlParamAnalysis analyzeSqlParams(String executableSql) {
        SqlParamAnalysis result = new SqlParamAnalysis();
        if (executableSql == null || executableSql.trim().isEmpty()) {
            return result;
        }

        try {
            List<SQLStatement> statements = SQLUtils.parseStatements(executableSql, JdbcConstants.MYSQL);
            if (statements == null || statements.isEmpty()) return result;

            SQLStatement statement = statements.get(0);

            // 使用访问者模式遍历
            statement.accept(new FullParamVisitor(result));

        } catch (Exception e) {
            log.debug("SQL 解析失败: {}", executableSql);
        }
        return result;
    }

    /**
     * 内部 Visitor：区分 SET 和 WHERE
     */
    private static class FullParamVisitor extends MySqlASTVisitorAdapter {
        private final SqlParamAnalysis analysis;

        public FullParamVisitor(SqlParamAnalysis analysis) {
            this.analysis = analysis;
        }

        // --- 1. 捕获 UPDATE 语句中的 SET 赋值 (SET name = '张三') ---
        @Override
        public boolean visit(SQLUpdateSetItem x) {
            // SET 子句中的赋值，只进 allParams，不进 whereParams
            if (isColumn(x.getColumn())) {
                String col = cleanName(x.getColumn().toString());
                String val = cleanValue(x.getValue().toString());

                analysis.getAllParams().put(col, val);
            }
            return true;
        }

        // --- 2. 捕获 二元操作 (age > 18, id = 1) ---
        // 这些通常出现在 WHERE, ON, HAVING 中
        @Override
        public boolean visit(SQLBinaryOpExpr x) {
            // 排除 SET 子句 (因为上面 visit(SQLUpdateSetItem) 已经处理了 SET)
            // 但 Druid 的 visitor 会递归，SQLUpdateSetItem 内部也是 BinaryOp (name = val)
            // 所以我们需要判断父节点，或者简单点：
            // 在 UPDATE 语句中，SQLUpdateSetItem 优先级更高。
            // 实际上 SQLUpdateSetItem.column = value，value 只是一个 Expr，不是 BinaryOpExpr (除非是 name = age + 1)
            // 简单的赋值 update set a=1，这里的 a=1 是 SQLUpdateSetItem 结构，而不是 SQLBinaryOpExpr

            processCondition(x.getLeft(), x.getRight());
            return true;
        }

        // --- 3. 捕获 IN 操作 (status IN (1, 2)) ---
        @Override
        public boolean visit(SQLInListExpr x) {
            if (isColumn(x.getExpr())) {
                String col = cleanName(x.getExpr().toString());
                StringBuilder sb = new StringBuilder();
                List<SQLExpr> list = x.getTargetList();
                for (int i = 0; i < list.size(); i++) {
                    sb.append(cleanValue(list.get(i).toString()));
                    if (i < list.size() - 1) sb.append(",");
                }

                String val = sb.toString();
                // IN 条件属于 Where 类
                analysis.getWhereParams().put(col, val);
                analysis.getAllParams().put(col, val);
            }
            return true;
        }

        private void processCondition(SQLExpr left, SQLExpr right) {
            String col = null;
            String val = null;

            if (isColumn(left) && !isColumn(right)) {
                col = cleanName(left.toString());
                val = cleanValue(right.toString());
            } else if (isColumn(right) && !isColumn(left)) {
                col = cleanName(right.toString());
                val = cleanValue(left.toString());
            }

            if (col != null) {
                // 条件操作：既放入 all，也放入 where
                analysis.getWhereParams().put(col, val);
                analysis.getAllParams().put(col, val);
            }
        }

        private boolean isColumn(SQLExpr expr) {
            return expr instanceof SQLIdentifierExpr || expr instanceof SQLPropertyExpr;
        }
    }

    private static String cleanName(String name) {
        if (name == null) return "";
        // 移除 MySQL 反引号 ` 和可能存在的单引号
        return name.replace("`", "").replace("'", "").replace("\"", "");
    }

    private static String cleanValue(String val) {
        if (val == null) return "NULL";
        val = val.trim();
        if (val.length() > 1 && val.startsWith("'") && val.endsWith("'")) {
            return val.substring(1, val.length() - 1);
        }
        return val;
    }
}