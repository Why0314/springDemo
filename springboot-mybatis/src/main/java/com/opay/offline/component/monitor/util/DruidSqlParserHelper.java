package com.opay.offline.component.monitor.util;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitorAdapter;
import com.alibaba.druid.util.JdbcConstants;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class DruidSqlParserHelper {

    /**
     * 全量解析 SQL 中的参数（包括 UPDATE SET 和 WHERE 条件）
     * 返回: { "name": "张三", "age": 18, "status": 1 }
     */
    public static Map<String, Object> extractAllParams(String executableSql) {
        Map<String, Object> paramsMap = new LinkedHashMap<>();
        if (executableSql == null || executableSql.trim().isEmpty()) {
            return paramsMap;
        }

        try {
            // 解析 SQL
            List<SQLStatement> statements = SQLUtils.parseStatements(executableSql, JdbcConstants.MYSQL);
            if (statements == null || statements.isEmpty()) return paramsMap;

            SQLStatement statement = statements.get(0);

            // 使用访问者模式遍历整个 AST 树
            // 这样无论是 WHERE 子句、SET 子句 还是 JOIN ON 子句，都能抓到
            statement.accept(new FullParamVisitor(paramsMap));

        } catch (Exception e) {
            log.debug("SQL 解析失败: {}", executableSql);
        }
        return paramsMap;
    }

    /**
     * 内部 Visitor：遍历所有节点寻找 列=值 的结构
     */
    private static class FullParamVisitor extends MySqlASTVisitorAdapter {
        private final Map<String, Object> resultMap;

        public FullParamVisitor(Map<String, Object> resultMap) {
            this.resultMap = resultMap;
        }

        // --- 1. 捕获 UPDATE 语句中的 SET 赋值 (name = '张三') ---
        @Override
        public boolean visit(SQLUpdateSetItem x) {
            if (x.getColumn() instanceof SQLIdentifierExpr || x.getColumn() instanceof SQLPropertyExpr) {
                String col = cleanName(x.getColumn().toString());
                String val = cleanValue(x.getValue().toString());
                resultMap.put(col, val);
            }
            return true;
        }

        // --- 2. 捕获 WHERE 和 ON 中的二元操作 (age > 18, id = 1) ---
        @Override
        public boolean visit(SQLBinaryOpExpr x) {
            SQLExpr left = x.getLeft();
            SQLExpr right = x.getRight();

            // 简单的左列右值判断
            if (isColumn(left) && !isColumn(right)) {
                resultMap.put(cleanName(left.toString()), cleanValue(right.toString()));
            } else if (isColumn(right) && !isColumn(left)) {
                resultMap.put(cleanName(right.toString()), cleanValue(left.toString()));
            }
            return true;
        }

        // --- 3. 捕获 IN 操作 (status IN (1, 2)) ---
        @Override
        public boolean visit(SQLInListExpr x) {
            if (isColumn(x.getExpr())) {
                String col = cleanName(x.getExpr().toString());
                StringBuilder sb = new StringBuilder();
                // 拼接 IN 里面的值
                List<SQLExpr> list = x.getTargetList();
                for (int i = 0; i < list.size(); i++) {
                    sb.append(cleanValue(list.get(i).toString()));
                    if (i < list.size() - 1) sb.append(",");
                }
                resultMap.put(col, sb.toString());
            }
            return true;
        }

        // --- 4. 捕获 INSERT INTO ... VALUES ... (比较复杂，可选) ---
        // INSERT 语句通常没有列名=值的直接对应，需要根据 index 对齐，
        // 商业监控通常更关注 WHERE 和 UPDATE SET，INSERT 保持 RawSQL 即可。

        private boolean isColumn(SQLExpr expr) {
            return expr instanceof SQLIdentifierExpr || expr instanceof SQLPropertyExpr;
        }
    }

    private static String cleanName(String name) {
        if (name == null) return "";
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