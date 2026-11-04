package com.bokaliai.terminal;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Shared provider-neutral tool catalog. It never executes commands or writes to GitHub. */
public final class AiToolRegistry {
    public static final int MAX_PLAN_STEPS = 6;
    public static final int MAX_PATCH_CHARS = 120000;

    public static final String READ_INFO = "READ_INFO";
    public static final String READ_LOGS = "READ_LOGS";
    public static final String APP_LIST = "APP_LIST";
    public static final String APP_LAUNCH = "APP_LAUNCH";
    public static final String APP_INSTALL_REMOVE = "APP_INSTALL_REMOVE";
    public static final String SCREEN_CONTROL = "SCREEN_CONTROL";
    public static final String DEVICE_SETTINGS = "DEVICE_SETTINGS";
    public static final String NETWORK_DIAGNOSTICS = "NETWORK_DIAGNOSTICS";
    public static final String RAW_ADB_SHELL = "RAW_ADB_SHELL";
    public static final String GITHUB_READ = "GITHUB_READ";
    public static final String GITHUB_PROPOSE = "GITHUB_PROPOSE";
    public static final String GITHUB_WRITE = "GITHUB_WRITE";
    public static final String TERMINAL_DIAGNOSTIC = "TERMINAL_DIAGNOSTIC";

    public static final class Tool {
        public final String name, scope, risk, description;
        private Tool(String name, String scope, String risk, String description) {
            this.name = name; this.scope = scope; this.risk = risk; this.description = description;
        }
    }

    private static final Map<String, Tool> TOOLS;
    static {
        Map<String, Tool> m = new HashMap<>();
        add(m, "adb.list_devices", READ_INFO, "LOW", "Liệt kê thiết bị ADB đã kết nối.");
        add(m, "adb.get_device_info", READ_INFO, "LOW", "Đọc thông tin thiết bị đã ghép nối.");
        add(m, "adb.read_logcat", READ_LOGS, "LOW", "Đọc logcat giới hạn; không xóa hoặc ghi log.");
        add(m, "adb.list_packages", APP_LIST, "LOW", "Liệt kê package trên thiết bị được chọn.");
        add(m, "adb.launch_app", APP_LAUNCH, "MEDIUM", "Mở một package cụ thể.");
        add(m, "adb.propose_shell_command", RAW_ADB_SHELL, "HIGH", "Chỉ đề xuất một lệnh shell để người dùng duyệt.");
        add(m, "adb.propose_device_change", DEVICE_SETTINGS, "HIGH", "Đề xuất thay đổi cài đặt; cần xác nhận.");
        add(m, "terminal.run_diagnostic", TERMINAL_DIAGNOSTIC, "MEDIUM", "Chẩn đoán cục bộ chỉ đọc trong Termux.");
        add(m, "github.read_file", GITHUB_READ, "LOW", "Đọc tệp trong repository đã cấp quyền.");
        add(m, "github.search_code", GITHUB_READ, "LOW", "Tìm mã nguồn trong repository.");
        add(m, "github.propose_patch", GITHUB_PROPOSE, "MEDIUM", "Tạo bản vá để xem xét; không commit/push.");
        add(m, "github.commit_patch", GITHUB_WRITE, "HIGH", "Commit bản vá đã duyệt; phải xác nhận repository, branch và diff.");
        TOOLS = Collections.unmodifiableMap(m);
    }

    private AiToolRegistry() { }
    private static void add(Map<String, Tool> m, String n, String s, String r, String d) {
        m.put(n, new Tool(n, s, r, d));
    }
    public static Tool find(String name) { return name == null ? null : TOOLS.get(name.trim()); }
    public static Set<String> names() { return Collections.unmodifiableSet(new HashSet<>(TOOLS.keySet())); }
    public static boolean hasScope(Set<String> grants, String scope) {
        return grants != null && scope != null && grants.contains(scope);
    }

    public static String promptCatalog() {
        StringBuilder out = new StringBuilder("DANH MỤC CÔNG CỤ BO (không tự cấp quyền):\n");
        for (Tool t : TOOLS.values()) out.append("- ").append(t.name)
            .append(" [scope=").append(t.scope).append(", risk=").append(t.risk)
            .append("]: ").append(t.description).append("\n");
        return out.append("Ứng dụng phải kiểm tra quyền, thiết bị/repository và đối số trước khi dispatch. ")
            .append("Tên công cụ không phải bằng chứng người dùng đã cấp quyền. ")
            .append("Giới hạn kế hoạch tương lai: ").append(MAX_PLAN_STEPS)
            .append(" bước; mặc định duyệt từng bước.").toString();
    }

    /** Shared validation for the existing single-command ADB plan pipeline. */
    public static String validateAdbPlan(String scope, String serial, String command,
                                         Set<String> grantedScopes) {
        if (serial == null || serial.trim().isEmpty() || serial.length() > 256
                || serial.matches(".*[\\r\\n\\u0000].*")) return "INVALID_DEVICE_SERIAL";
        if (!hasScope(grantedScopes, scope)) return "SCOPE_NOT_GRANTED";
        if (command == null || command.trim().isEmpty() || command.length() > 1200
                || command.matches(".*[\\r\\n;|&<>\\x60$].*")) return "INVALID_SINGLE_COMMAND";
        if (containsBlockedOperation(command)) return "BLOCKED_COMMAND";
        if (!Arrays.asList(READ_INFO, READ_LOGS, APP_LIST, APP_LAUNCH, APP_INSTALL_REMOVE,
                SCREEN_CONTROL, DEVICE_SETTINGS, NETWORK_DIAGNOSTICS, RAW_ADB_SHELL).contains(scope))
            return "UNKNOWN_ADB_SCOPE";
        return "OK";
    }

    /**
     * Strict OpenAI Responses function schema for the single-command ADB proposal.
     * This tool only creates a proposal; it is not an execution endpoint.
     */
    public static JSONArray openAiProposalTools() throws org.json.JSONException {
        JSONObject properties = new JSONObject()
            .put("serial", new JSONObject().put("type", "string")
                .put("description", "Serial chính xác của thiết bị ADB đã được người dùng chọn."))
            .put("scope", new JSONObject().put("type", "string")
                .put("enum", new org.json.JSONArray().put(READ_INFO).put(READ_LOGS)
                    .put(APP_LIST).put(APP_LAUNCH).put(APP_INSTALL_REMOVE)
                    .put(SCREEN_CONTROL).put(DEVICE_SETTINGS).put(NETWORK_DIAGNOSTICS)
                    .put(RAW_ADB_SHELL)))
            .put("command", new JSONObject().put("type", "string")
                .put("description", "Đúng một lệnh Android shell; không thêm adb -s."))
            .put("reason", new JSONObject().put("type", "string"))
            .put("risk", new JSONObject().put("type", "string")
                .put("enum", new org.json.JSONArray().put("LOW").put("MEDIUM").put("HIGH")));
        JSONObject parameters = new JSONObject().put("type", "object")
            .put("properties", properties)
            .put("required", new org.json.JSONArray().put("serial").put("scope")
                .put("command").put("reason").put("risk"))
            .put("additionalProperties", false);
        JSONObject function = new JSONObject().put("type", "function")
            .put("name", "adb.propose_shell_command")
            .put("description", "Đề xuất một lệnh ADB duy nhất cho thiết bị được người dùng chỉ định. Không thực thi. BO sẽ kiểm tra quyền và yêu cầu người dùng xác nhận.")
            .put("parameters", parameters).put("strict", true);
        return new JSONArray().put(function);
    }

    /**
     * Gemini generateContent REST tool declaration for the same guarded ADB proposal.
     * Gemini may suggest the call; BO remains responsible for validation and approval.
     */
    public static JSONArray geminiProposalTools() throws org.json.JSONException {
        JSONObject properties = new JSONObject()
            .put("serial", new JSONObject().put("type", "STRING")
                .put("description", "Serial chính xác của thiết bị ADB đã được người dùng chọn."))
            .put("scope", new JSONObject().put("type", "STRING")
                .put("enum", new JSONArray().put(READ_INFO).put(READ_LOGS)
                    .put(APP_LIST).put(APP_LAUNCH).put(APP_INSTALL_REMOVE)
                    .put(SCREEN_CONTROL).put(DEVICE_SETTINGS).put(NETWORK_DIAGNOSTICS)
                    .put(RAW_ADB_SHELL)))
            .put("command", new JSONObject().put("type", "STRING")
                .put("description", "Đúng một lệnh Android shell; không thêm adb -s."))
            .put("reason", new JSONObject().put("type", "STRING"))
            .put("risk", new JSONObject().put("type", "STRING")
                .put("enum", new JSONArray().put("LOW").put("MEDIUM").put("HIGH")));
        JSONObject parameters = new JSONObject().put("type", "OBJECT")
            .put("properties", properties)
            .put("required", new JSONArray().put("serial").put("scope")
                .put("command").put("reason").put("risk"));
        JSONObject declaration = new JSONObject()
            .put("name", "adb_propose_shell_command")
            .put("description", "Đề xuất một lệnh ADB duy nhất; không thực thi. BO sẽ kiểm tra quyền và yêu cầu người dùng xác nhận.")
            .put("parameters", parameters);
        return new JSONArray().put(new JSONObject()
            .put("function_declarations", new JSONArray().put(declaration)));
    }

    /** Anthropic Messages API tool declaration for the guarded ADB proposal. */
    public static JSONArray claudeProposalTools() throws org.json.JSONException {
        JSONObject properties = new JSONObject()
            .put("serial", new JSONObject().put("type", "string")
                .put("description", "Serial chính xác của thiết bị ADB đã được người dùng chọn."))
            .put("scope", new JSONObject().put("type", "string")
                .put("enum", new JSONArray().put(READ_INFO).put(READ_LOGS)
                    .put(APP_LIST).put(APP_LAUNCH).put(APP_INSTALL_REMOVE)
                    .put(SCREEN_CONTROL).put(DEVICE_SETTINGS).put(NETWORK_DIAGNOSTICS)
                    .put(RAW_ADB_SHELL)))
            .put("command", new JSONObject().put("type", "string")
                .put("description", "Đúng một lệnh Android shell; không thêm adb -s."))
            .put("reason", new JSONObject().put("type", "string"))
            .put("risk", new JSONObject().put("type", "string")
                .put("enum", new JSONArray().put("LOW").put("MEDIUM").put("HIGH")));
        JSONObject schema = new JSONObject().put("type", "object")
            .put("properties", properties)
            .put("required", new JSONArray().put("serial").put("scope")
                .put("command").put("reason").put("risk"))
            .put("additionalProperties", false);
        JSONObject tool = new JSONObject()
            .put("name", "adb_propose_shell_command")
            .put("description", "Đề xuất một lệnh ADB duy nhất; không thực thi. BO kiểm tra quyền và yêu cầu người dùng xác nhận.")
            .put("input_schema", schema)
            .put("strict", true);
        return new JSONArray().put(tool);
    }

    /** Extract Anthropic Messages API tool_use blocks; malformed calls are ignored. */
    public static JSONArray extractClaudeToolUses(JSONObject response) {
        JSONArray calls = new JSONArray();
        if (response == null) return calls;
        JSONArray content = response.optJSONArray("content");
        if (content == null) return calls;
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block == null || !"tool_use".equals(block.optString("type"))) continue;
            String id = block.optString("id", "").trim();
            String name = block.optString("name", "").trim();
            JSONObject input = block.optJSONObject("input");
            if (id.isEmpty() || name.isEmpty() || input == null) continue;
            calls.put(new JSONObject().put("tool_use_id", id)
                .put("name", name).put("arguments", input));
        }
        return calls;
    }

    /** Extract Gemini generateContent functionCall parts; malformed calls are ignored. */
    public static JSONArray extractGeminiFunctionCalls(JSONObject response) {
        JSONArray calls = new JSONArray();
        if (response == null) return calls;
        JSONArray candidates = response.optJSONArray("candidates");
        if (candidates == null) return calls;
        for (int c = 0; c < candidates.length(); c++) {
            JSONObject candidate = candidates.optJSONObject(c);
            JSONObject content = candidate == null ? null : candidate.optJSONObject("content");
            JSONArray parts = content == null ? null : content.optJSONArray("parts");
            if (parts == null) continue;
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part == null) continue;
                JSONObject functionCall = part.optJSONObject("functionCall");
                if (functionCall == null) functionCall = part.optJSONObject("function_call");
                if (functionCall == null) continue;
                String name = functionCall.optString("name", "").trim();
                JSONObject args = functionCall.optJSONObject("args");
                if (args == null) args = functionCall.optJSONObject("arguments");
                if (name.isEmpty() || args == null) continue;
                calls.put(new JSONObject().put("name", name).put("arguments", args));
            }
        }
        return calls;
    }

    /** Extract direct Responses API function_call items; malformed calls are ignored. */
    public static JSONArray extractOpenAiFunctionCalls(JSONObject response) {
        JSONArray calls = new JSONArray();
        if (response == null) return calls;
        JSONArray output = response.optJSONArray("output");
        if (output == null) return calls;
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null || !"function_call".equals(item.optString("type"))) continue;
            String callId = item.optString("call_id", "").trim();
            String name = item.optString("name", "").trim();
            String rawArgs = item.optString("arguments", "");
            if (callId.isEmpty() || name.isEmpty() || rawArgs.isEmpty()) continue;
            try {
                JSONObject args = new JSONObject(rawArgs);
                calls.put(new JSONObject().put("call_id", callId)
                    .put("name", name).put("arguments", args));
            } catch (org.json.JSONException ignored) {
                // Never pass malformed model-generated arguments to a dispatcher.
            }
        }
        return calls;
    }

    /** Validate the call envelope only; host UI must still obtain approval before side effects. */
    public static String validateCall(String name, JSONObject args, Set<String> grants) {
        Tool t = find(name);
        if (t == null) return "UNKNOWN_TOOL";
        if (!hasScope(grants, t.scope)) return "SCOPE_NOT_GRANTED";
        if (args == null) return "MISSING_ARGUMENTS";

        if (name.startsWith("adb.")) {
            String serial = args.optString("serial", "").trim();
            if (serial.isEmpty() || serial.length() > 256 || serial.matches(".*[\\r\\n\\u0000].*"))
                return "INVALID_DEVICE_SERIAL";
        }
        if ("adb.propose_shell_command".equals(name) || "terminal.run_diagnostic".equals(name)) {
            String command = args.optString("command", "").trim();
            int max = "adb.propose_shell_command".equals(name) ? 1200 : 500;
            if (command.isEmpty() || command.length() > max
                    || command.matches(".*[\\r\\n;|&<>\\x60$].*")) return "INVALID_SINGLE_COMMAND";
            if (containsBlockedOperation(command)) return "BLOCKED_COMMAND";
        }
        if (name.startsWith("github.")) {
            String repo = args.optString("repository", "").trim();
            if (!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) return "INVALID_REPOSITORY";
        }
        if ("github.read_file".equals(name) || "github.propose_patch".equals(name)
                || "github.commit_patch".equals(name)) {
            if (!validRepoPath(args.optString("path", ""))) return "INVALID_REPOSITORY_PATH";
        }
        if ("github.propose_patch".equals(name) || "github.commit_patch".equals(name)) {
            String patch = args.optString("patch", "");
            if (patch.isEmpty() || patch.length() > MAX_PATCH_CHARS) return "INVALID_PATCH_SIZE";
            if ("github.commit_patch".equals(name) && args.optString("branch", "").trim().isEmpty())
                return "BRANCH_REQUIRED";
        }
        return "OK";
    }

    public static boolean validRepoPath(String path) {
        if (path == null || path.trim().isEmpty() || path.length() > 500) return false;
        String p = path.trim();
        if (p.startsWith("/") || p.startsWith("\\\\") || p.contains("..")
                || p.matches(".*[\\r\\n\\u0000].*")) return false;
        return !Arrays.asList(p.split("/")).contains("");
    }

    private static boolean containsBlockedOperation(String command) {
        String c = command.toLowerCase(Locale.ROOT);
        String[] blocked = {"rm ", "reboot", "shutdown", "factory_reset", "factory reset",
            "wipe", "mkfs", "dd ", "su ", "setenforce", "passwd", "fastboot", "pm clear",
            "disable-user", "resetprop", "mount ", "umount ", "chmod ", "chown ",
            "logcat -c", "logcat -f", "curl ", "wget ", "nc ", "netcat "};
        for (String token : blocked) if (c.contains(token)) return true;
        return false;
    }
}
