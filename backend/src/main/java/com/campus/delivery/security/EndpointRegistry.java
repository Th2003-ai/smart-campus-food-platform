package com.campus.delivery.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Endpoint registry in ZenTao style: every controller method is a privilege item
 * named module.method, where module = controller class name without the
 * "Controller" suffix (lower case) and method = the java handler method name.
 *
 * <p>These names are the same ones {@link AuthInterceptor} checks at runtime, so
 * the admin matrix always lists the endpoints that really exist in the code
 * (a privilege row alone can never invent a phantom endpoint).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EndpointRegistry {

    private final RequestMappingHandlerMapping handlerMapping;

    /** module -> [method], both from live code */
    public Map<String, Object> tree() {
        Map<String, Set<String>> byModule = new LinkedHashMap<>();
        Map<String, Set<String>> paths = new LinkedHashMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod hm = e.getValue();
            String module = moduleOf(hm);
            String method = hm.getMethod().getName();
            byModule.computeIfAbsent(module, k -> new TreeSet<>()).add(method);
            String url = describe(e.getKey());
            if (url != null) {
                paths.computeIfAbsent(module + "." + method, k -> new LinkedHashSet<>()).add(url);
            }
        }
        List<String> modules = new ArrayList<>(byModule.keySet());
        modules.sort(Comparator.naturalOrder());
        List<Map<String, Object>> out = new ArrayList<>();
        int total = 0;
        for (String module : modules) {
            List<Map<String, Object>> methods = new ArrayList<>();
            for (String method : byModule.get(module)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("method", method);
                item.put("key", module + "." + method);
                item.put("paths", new ArrayList<>(paths.getOrDefault(module + "." + method, Set.of())));
                methods.add(item);
            }
            total += methods.size();
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("module", module);
            node.put("methods", methods);
            out.add(node);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modules", out);
        result.put("total", total);
        return result;
    }

    /** module name used by both the registry and the interceptor */
    public static String moduleOf(HandlerMethod hm) {
        String simple = hm.getBeanType().getSimpleName();
        if (simple.endsWith("Controller")) {
            simple = simple.substring(0, simple.length() - "Controller".length());
        }
        return simple.toLowerCase();
    }

    private String describe(RequestMappingInfo info) {
        Set<RequestMethod> verbs = info.getMethodsCondition().getMethods();
        String verb = verbs.isEmpty() ? "ANY" : verbs.iterator().next().name();
        Set<String> patterns = info.getPatternValues();
        if (patterns.isEmpty()) {
            return null;
        }
        String p = patterns.iterator().next();
        return verb + " " + p;
    }
}
