package com.ai4se.runtime.demo.delivery.stress;

import com.ai4se.runtime.demo.delivery.DeliveryScenario;
import java.util.LinkedHashMap;
import java.util.Map;

/** Type: 跨模块修改 — fix core tax + api facade policy together. */
public final class CrossModuleScenario implements DeliveryScenario {

    @Override
    public String id() {
        return "03-cross-module";
    }

    @Override
    public String typeLabel() {
        return "跨模块修改";
    }

    @Override
    public String requirement() {
        return "Fix PriceCalculator (core) to apply 10% tax and update OrderFacade.taxPolicy() "
                + "(api) to report tax=10%. Both modules must pass OrderFacadeTest.";
    }

    @Override
    public String projectId() {
        return "stress-03-cross-module";
    }

    @Override
    public String fixtureDirName() {
        return "stress-workspaces/03-cross-module";
    }

    @Override
    public String discoveryCommand() {
        return "git status";
    }

    @Override
    public String verifyCommand() {
        return "mvn -f pom.xml -q test";
    }

    @Override
    public Map<String, String> planFiles() {
        Map<String, String> files = new LinkedHashMap<String, String>();
        files.put("PLAN.md", ""
                + "# Plan\n\n"
                + "1. Change core.PriceCalculator to 10% tax.\n"
                + "2. Change api.OrderFacade.taxPolicy to tax=10%.\n"
                + "3. Update README.\n"
                + "4. mvn test.\n");
        return files;
    }

    @Override
    public Map<String, String> executionFiles() {
        Map<String, String> files = new LinkedHashMap<String, String>();
        files.put("src/main/java/com/example/core/PriceCalculator.java", ""
                + "package com.example.core;\n\n"
                + "public final class PriceCalculator {\n\n"
                + "    public int totalWithTaxCents(int netCents) {\n"
                + "        return netCents + (netCents * 10 / 100);\n"
                + "    }\n"
                + "}\n");
        files.put("src/main/java/com/example/api/OrderFacade.java", ""
                + "package com.example.api;\n\n"
                + "import com.example.core.PriceCalculator;\n\n"
                + "public final class OrderFacade {\n\n"
                + "    private final PriceCalculator calculator = new PriceCalculator();\n\n"
                + "    public int quoteTotalCents(int netCents) {\n"
                + "        return calculator.totalWithTaxCents(netCents);\n"
                + "    }\n\n"
                + "    public String taxPolicy() {\n"
                + "        return \"tax=10%\";\n"
                + "    }\n"
                + "}\n");
        files.put("README.md", ""
                + "# Cross-module stress sample\n\n"
                + "`core.PriceCalculator` applies 10% tax; `api.OrderFacade` exposes quotes and "
                + "`taxPolicy()=tax=10%`.\n");
        return files;
    }
}
