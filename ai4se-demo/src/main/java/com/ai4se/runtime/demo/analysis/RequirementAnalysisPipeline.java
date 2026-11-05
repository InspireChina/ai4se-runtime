package com.ai4se.runtime.demo.analysis;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pilot / Engineering Requirement Analysis Pipeline.
 * No Runtime / Worker / Graph / Coding patches.
 */
public final class RequirementAnalysisPipeline {

    public static final String DEFAULT_REQUIREMENT = "订单接口增加 timeout 配置。";

    /** Continuous DoD on first-delivery-workspace (compilable, non-pilot-order fixture). */
    public static final String FIRST_DELIVERY_REQUIREMENT = ""
            + "Fix ConfigService so timeoutMs() reads app.timeout.ms from application.properties "
            + "(expected 5000). Add TimeoutSource. Document app.timeout.ms in README. "
            + "Verify with Maven tests.";

    public static final String REST_API_REQUIREMENT = ""
            + "Add UserApi handling GET /api/users/{id}: 200 JSON for known ids, "
            + "404 JSON error for unknown. Document endpoint in README.";

    public static final String PROMOTION_REQUIREMENT = ""
            + "电商后台 Promotion Service 新增促销类型：满减 + 满折。\n"
            + "支持多个活动；每活动含名称、生效/结束时间、是否启用；\n"
            + "支持多优惠阶梯（如满100减20、满300减80、满500打9折）；\n"
            + "每订单只命中一个最高优惠；优惠类型含固定金额与百分比折扣；\n"
            + "百分比折扣最高优惠金额可配置；\n"
            + "后台接口：Create/Update/Query Promotion、Order Calculate Promotion；\n"
            + "不能影响已有优惠；无命中返回 NoPromotion。\n"
            + "已有数据库/OrderService/Promotion：未知。";

    private RequirementAnalysisPipeline() {
    }

    public static Result run(
            Path workspace,
            String requirement,
            Path bundleOut,
            Map<String, String> clarificationAnswers) throws Exception {
        return run(workspace, requirement, bundleOut, clarificationAnswers, false, "pilot-analysis");
    }

    public static Result run(
            Path workspace,
            String requirement,
            Path bundleOut,
            Map<String, String> clarificationAnswers,
            boolean engineeringHonestUnknown,
            String profileId) throws Exception {
        return run(workspace, requirement, bundleOut, clarificationAnswers,
                engineeringHonestUnknown, profileId, 1, false);
    }

    /**
     * @param clarificationRound     本需求澄清轮次（从 1 起），供 §2.3 停止条件
     * @param previousRoundBlocked   上一轮 Gap 是否 BLOCKED（连续两轮规则）
     */
    public static Result run(
            Path workspace,
            String requirement,
            Path bundleOut,
            Map<String, String> clarificationAnswers,
            boolean engineeringHonestUnknown,
            String profileId,
            int clarificationRound,
            boolean previousRoundBlocked) throws Exception {
        // engineeringHonestUnknown: stronger risks; UNKNOWN answers never unlock Planning.
        String req = requirement == null || requirement.trim().isEmpty()
                ? DEFAULT_REQUIREMENT
                : requirement.trim();

        MapSearchAnalyzer analyzer = new MapSearchAnalyzer();
        RepositoryFacts facts = analyzer.analyze(workspace, req);
        RepositoryContext context = ContextBuilder.build(req, facts);

        Map<String, String> answers = clarificationAnswers == null
                ? new LinkedHashMap<String, String>()
                : new LinkedHashMap<String, String>(clarificationAnswers);

        ClarificationSession clarification = ClarificationSession.fromContext(context, answers);
        GapReport gap = GapDetector.detect(context, clarification.getAnswers(), engineeringHonestUnknown);

        // Timeout pilot only: auto-answer narrow timeout key when no answers provided.
        if (!engineeringHonestUnknown
                && !gap.mayPlan()
                && (clarificationAnswers == null || clarificationAnswers.isEmpty())
                && !ContextBuilder.isPromotionRequirement(req)) {
            Map<String, String> pilotAnswers = new LinkedHashMap<String, String>();
            pilotAnswers.put("timeout", "app.order.timeout.ms=3000");
            clarification = ClarificationSession.fromContext(context, pilotAnswers);
            gap = GapDetector.detect(context, clarification.getAnswers(), false);
        }

        boolean refused = false;
        for (String v : clarification.getAnswers().values()) {
            if (StopCondition.isRefuseAnswer(v)) {
                refused = true;
                break;
            }
        }
        boolean gapBlocked = gap.getStatus() == GapReport.Status.BLOCKED;
        StopCondition.Result stop = StopCondition.evaluate(
                clarificationRound, gapBlocked, previousRoundBlocked, refused);

        String plan = null;
        // §2.3: STOP forbids Planning even if Gap somehow mayPlan.
        if (gap.mayPlan() && !stop.isStop()) {
            plan = PlanDrafting.draft(req, context, gap);
        }

        DeliveryBundleWriter.write(
                bundleOut,
                req,
                facts,
                context,
                gap,
                clarification,
                plan,
                profileId,
                stop,
                clarificationRound);

        return new Result(req, facts, context, gap, clarification, plan != null, bundleOut, stop);
    }

    /** Honest UNKNOWN answers for promotion engineering validation (not schema invention). */
    public static Map<String, String> promotionUnknownAnswers() {
        Map<String, String> answers = new LinkedHashMap<String, String>();
        answers.put("promotion_table", "UNKNOWN — Facts 未证明 Promotion 表；待确认");
        answers.put("discount_model", "UNKNOWN — Facts 未证明折扣模型；待确认");
        answers.put("multi_promotion", "UNKNOWN — Facts 未证明多活动并存行为；待确认");
        answers.put("order_calc", "UNKNOWN — Facts 未证明 Order 计价接口；待确认");
        answers.put("compat", "UNKNOWN — 兼容约束待确认；Spec 要求不得影响已有优惠");
        answers.put("best_offer_rule", "UNKNOWN — 「最高优惠」比较规则待确认");
        answers.put("percent_cap", "UNKNOWN — 百分比封顶配置位置待确认");
        return answers;
    }

    /**
     * Controllable concrete answers for Gap re-check → ASSUMABLE → Planning.
     * Greenfield only — does <strong>not</strong> invent Facts that tables/services exist in the placeholder workspace.
     */
    public static Map<String, String> promotionConcreteGreenfieldAnswers() {
        Map<String, String> answers = new LinkedHashMap<String, String>();
        answers.put("promotion_table",
                "否 — 绿场新建；本 fixture 无既有 Promotion 表；新建 persistence（表名待实现阶段定，Analysis 不写 DDL）");
        answers.put("discount_model",
                "否 — 无既有折扣领域模型；按 Spec 新建固定金额满减 + 百分比满折两种类型");
        answers.put("multi_promotion",
                "否 — 无既有多活动行为；按 Spec：允许多活动并存，每订单只命中一个最高优惠");
        answers.put("order_calc",
                "否 — Facts 无 OrderService；绿场提供 Order Calculate Promotion 入口（路径实现阶段定）");
        answers.put("compat",
                "绿场无既有优惠类型可破坏；约束=新类型不得改变「无命中→NoPromotion」语义；后续若接入真仓须重澄清");
        answers.put("best_offer_rule",
                "按优惠金额比较：取减免金额最大者；金额相同取优先级字段更大者；仍平局取创建时间更早");
        answers.put("percent_cap",
                "活动级配置：每个满折活动自带 maxDiscountAmount；无全局封顶");
        return answers;
    }

    /** Fuzzy answers that must NOT unlock Planning (answer-quality gate). */
    public static Map<String, String> promotionFuzzyAnswers() {
        Map<String, String> answers = new LinkedHashMap<String, String>();
        answers.put("promotion_table", "Promotion 应该已经有了");
        answers.put("discount_model", "应该有吧");
        answers.put("multi_promotion", "大概支持");
        answers.put("order_calc", "好像有 Order 接口");
        answers.put("compat", "应该兼容");
        answers.put("best_offer_rule", "可能按金额比");
        answers.put("percent_cap", "估计在配置里");
        return answers;
    }

    public static final class Result {
        public final String requirement;
        public final RepositoryFacts facts;
        public final RepositoryContext context;
        public final GapReport gap;
        public final ClarificationSession clarification;
        public final boolean planWritten;
        public final Path bundleDir;
        public final StopCondition.Result stop;

        Result(
                String requirement,
                RepositoryFacts facts,
                RepositoryContext context,
                GapReport gap,
                ClarificationSession clarification,
                boolean planWritten,
                Path bundleDir,
                StopCondition.Result stop) {
            this.requirement = requirement;
            this.facts = facts;
            this.context = context;
            this.gap = gap;
            this.clarification = clarification;
            this.planWritten = planWritten;
            this.bundleDir = bundleDir;
            this.stop = stop;
        }
    }
}
