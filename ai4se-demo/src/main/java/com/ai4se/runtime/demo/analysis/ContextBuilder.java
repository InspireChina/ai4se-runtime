package com.ai4se.runtime.demo.analysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pipeline step: Facts + Spec → Repository Context (no Plan / Patch). */
public final class ContextBuilder {

    private ContextBuilder() {
    }

    public static RepositoryContext build(String requirement, RepositoryFacts facts) {
        List<String> candidates = new ArrayList<String>();
        List<String> topK = new ArrayList<String>();
        Set<String> modules = new LinkedHashSet<String>();
        for (RepositoryFacts.Hit hit : facts.getHits()) {
            candidates.add(hit.getPath());
            topK.add(hit.getPath() + " | score=" + hit.getScore() + " | " + hit.getExcerpt());
            if (hit.getPath().contains("/src/main/java/")) {
                modules.add(moduleHint(hit.getPath()));
            }
        }
        for (String m : facts.getModules()) {
            if (relatedModule(m, requirement)) {
                modules.add(m);
            }
        }

        List<String> unknown = new ArrayList<String>();
        List<String> needClarify = new ArrayList<String>();

        boolean promotionReq = isPromotionRequirement(requirement);
        boolean timeoutReq = requirementContains(requirement, "timeout")
                || requirementContains(requirement, "超时");

        boolean hasPromotionSurface = pathHints(candidates, "promotion", "promo", "满减", "满折", "discount");
        boolean hasOrderSurface = pathHints(candidates, "order", "订单");
        boolean hasTimeoutSurface = false;
        for (RepositoryFacts.Hit hit : facts.getHits()) {
            String ex = hit.getExcerpt() == null ? "" : hit.getExcerpt().toLowerCase(Locale.ROOT);
            String p = hit.getPath().toLowerCase(Locale.ROOT);
            if (ex.contains("timeout") || p.contains("timeout")) {
                hasTimeoutSurface = true;
                break;
            }
        }

        if (promotionReq) {
            if (!hasPromotionSurface) {
                unknown.add("No Promotion / discount implementation surface found in repository Facts.");
                needClarify.add("是否已有 Promotion 表（或等价持久化）？表结构是否已知？");
                needClarify.add("是否已有折扣/优惠领域模型（固定金额、百分比）？");
                needClarify.add("现有 Promotion 是否已支持多个活动并存？");
            }
            if (!hasOrderSurface) {
                unknown.add("No Order calculation / OrderService surface found in repository Facts.");
                needClarify.add("Order 是否已经存在金额计算接口（或 OrderService 计价入口）？路径是什么？");
            }
            unknown.add("Existing database schema for promotions is unknown from Facts.");
            needClarify.add("新满减/满折是否必须兼容并不得破坏已有优惠类型？兼容约束是什么？");
            needClarify.add("「每个订单只能命中一个最高优惠」的比较规则（按优惠金额？按优先级？）是否已有定义？");
            needClarify.add("百分比折扣的「最高优惠金额」配置存放在哪里（活动级/全局）？");
        }

        if (timeoutReq && !hasTimeoutSurface) {
            unknown.add("Default timeout value and config key name are not present in repo Facts.");
            needClarify.add("timeout 配置项名称与默认值是多少？");
        }

        if (!promotionReq && !timeoutReq) {
            if (candidates.isEmpty()) {
                unknown.add("No relevant files matched requirement keywords in Facts.");
                needClarify.add("请确认需求对应的模块/仓库路径。");
            }
        }

        String confidence;
        if (needClarify.isEmpty() && !candidates.isEmpty()) {
            confidence = "high";
        } else if (!candidates.isEmpty()) {
            confidence = "medium";
        } else {
            confidence = "low";
        }

        return new RepositoryContext(
                candidates,
                new ArrayList<String>(modules),
                topK,
                confidence,
                unknown,
                needClarify);
    }

    static boolean isPromotionRequirement(String requirement) {
        return requirementContains(requirement, "promotion")
                || requirementContains(requirement, "促销")
                || requirementContains(requirement, "满减")
                || requirementContains(requirement, "满折")
                || requirementContains(requirement, "优惠");
    }

    private static boolean relatedModule(String module, String requirement) {
        String m = module.toLowerCase(Locale.ROOT);
        if (m.contains("order") || m.contains("promo") || m.contains("promotion") || m.contains("discount")) {
            return true;
        }
        return requirementContains(requirement, "订单") || requirementContains(requirement, "促销");
    }

    private static boolean pathHints(List<String> paths, String... tokens) {
        for (String c : paths) {
            String lower = c.toLowerCase(Locale.ROOT);
            for (String t : tokens) {
                if (lower.contains(t.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean requirementContains(String requirement, String token) {
        return requirement != null && requirement.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }

    private static String moduleHint(String path) {
        String marker = "/src/main/java/";
        int idx = path.indexOf(marker);
        if (idx < 0) {
            return path;
        }
        String rest = path.substring(idx + marker.length());
        int slash = rest.lastIndexOf('/');
        if (slash <= 0) {
            return rest;
        }
        return rest.substring(0, slash).replace('/', '.');
    }
}
