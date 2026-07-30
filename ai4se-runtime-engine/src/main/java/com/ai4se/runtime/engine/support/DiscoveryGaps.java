package com.ai4se.runtime.engine.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * S8a — gaps from Map+Search hits (摸底≠懂业务；澄清才补齐).
 * Questions are derived from the same gap list (not a parallel hardcoded FAQ).
 */
public final class DiscoveryGaps {

    public static final class Outcome {
        public final List<String> gaps;
        public final List<String> questions;

        Outcome(List<String> gaps, List<String> questions) {
            this.gaps = Collections.unmodifiableList(new ArrayList<String>(gaps));
            this.questions = Collections.unmodifiableList(new ArrayList<String>(questions));
        }
    }

    private DiscoveryGaps() {
    }

    public static Outcome detect(String requirement, List<MapSearchDiscovery.Hit> hits) {
        List<String> gaps = new ArrayList<String>();
        List<String> questions = new ArrayList<String>();
        String req = requirement == null ? "" : requirement;

        boolean promotionReq = contains(req, "promotion", "促销", "满减", "满折", "优惠");
        boolean timeoutReq = contains(req, "timeout", "超时");
        boolean hasPromotionSurface = pathHints(hits, "promotion", "promo", "discount", "满减");
        boolean hasOrderSurface = pathHints(hits, "order", "订单");
        boolean hasTimeoutSurface = false;
        for (MapSearchDiscovery.Hit hit : hits) {
            String ex = hit.excerpt == null ? "" : hit.excerpt.toLowerCase(Locale.ROOT);
            String p = hit.path.toLowerCase(Locale.ROOT);
            if (ex.contains("timeout") || p.contains("timeout") || ex.contains("超时")) {
                hasTimeoutSurface = true;
                break;
            }
        }

        if (promotionReq) {
            if (!hasPromotionSurface) {
                addGap(gaps, questions,
                        "No Promotion / discount implementation surface found in repository Facts.",
                        "是否已有 Promotion 表（或等价持久化）？表结构是否已知？");
                addGap(gaps, questions,
                        "Promotion domain model unknown from Facts.",
                        "是否已有折扣/优惠领域模型（固定金额、百分比）？");
            }
            if (!hasOrderSurface) {
                addGap(gaps, questions,
                        "No Order calculation surface found in repository Facts.",
                        "Order 是否已经存在金额计算接口？路径是什么？");
            }
        }

        if (timeoutReq && !hasTimeoutSurface) {
            addGap(gaps, questions,
                    "Default timeout value and config key name are not present in repo Facts.",
                    "timeout 配置项名称与默认值是多少？");
        }

        if (!promotionReq && !timeoutReq && hits.isEmpty()) {
            addGap(gaps, questions,
                    "No relevant files matched requirement keywords in Facts.",
                    "请确认需求对应的模块/仓库路径。");
        }

        return new Outcome(gaps, questions);
    }

    private static void addGap(List<String> gaps, List<String> questions, String gap, String question) {
        gaps.add(gap);
        questions.add(question);
    }

    private static boolean contains(String req, String... tokens) {
        String lower = req.toLowerCase(Locale.ROOT);
        for (String t : tokens) {
            if (lower.contains(t.toLowerCase(Locale.ROOT)) || req.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static boolean pathHints(List<MapSearchDiscovery.Hit> hits, String... tokens) {
        for (MapSearchDiscovery.Hit h : hits) {
            String lower = h.path.toLowerCase(Locale.ROOT);
            for (String t : tokens) {
                if (lower.contains(t.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }
}
