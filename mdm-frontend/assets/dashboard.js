(function () {
    "use strict";

    const CONFIG_KEY = "mdm-dashboard-config-v2";

    const el = {
        baseUrl: document.getElementById("baseUrl"),
        adminUser: document.getElementById("adminUser"),
        adminPass: document.getElementById("adminPass"),
        deviceApiKey: document.getElementById("deviceApiKey"),
        healthBadge: document.getElementById("healthBadge"),
        inactiveMinutes: document.getElementById("inactiveMinutes"),
        auditImei: document.getElementById("auditImei"),
        versionBars: document.getElementById("versionBars"),
        regionBars: document.getElementById("regionBars"),
        groupBars: document.getElementById("groupBars"),
        failureStageBars: document.getElementById("failureStageBars"),
        heatmapBody: document.getElementById("heatmapBody"),
        metricTotal: document.getElementById("metricTotal"),
        metricActive: document.getElementById("metricActive"),
        metricInactive: document.getElementById("metricInactive"),
        metricSuccess: document.getElementById("metricSuccess"),
        metricFailure: document.getElementById("metricFailure"),
        metricPending: document.getElementById("metricPending"),
        metricRollout: document.getElementById("metricRollout"),
        metricSuccessRate: document.getElementById("metricSuccessRate"),
        metricFailureRate: document.getElementById("metricFailureRate"),
        schedulesBody: document.getElementById("schedulesBody"),
        auditList: document.getElementById("auditList"),
        consoleOutput: document.getElementById("consoleOutput"),
        saveConfigBtn: document.getElementById("saveConfigBtn"),
        checkHealthBtn: document.getElementById("checkHealthBtn"),
        refreshDashboardBtn: document.getElementById("refreshDashboardBtn"),
        refreshSchedulesBtn: document.getElementById("refreshSchedulesBtn"),
        refreshAuditBtn: document.getElementById("refreshAuditBtn"),
        clearConsoleBtn: document.getElementById("clearConsoleBtn"),
        createVersionForm: document.getElementById("createVersionForm"),
        compatibilityForm: document.getElementById("compatibilityForm"),
        scheduleForm: document.getElementById("scheduleForm"),
        approveScheduleForm: document.getElementById("approveScheduleForm"),
        heartbeatForm: document.getElementById("heartbeatForm"),
        statusForm: document.getElementById("statusForm")
    };

    const fmt = new Intl.DateTimeFormat("en-IN", {
        year: "numeric",
        month: "short",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit"
    });

    function nowTag() {
        return fmt.format(new Date());
    }

    function log(type, text, data) {
        const dataText = data === undefined ? "" : `\n${JSON.stringify(data, null, 2)}`;
        if (!el.consoleOutput) {
            console.log(`[${nowTag()}] ${type}: ${text}`, data || "");
            return;
        }
        el.consoleOutput.textContent = `[${nowTag()}] ${type}: ${text}${dataText}\n\n${el.consoleOutput.textContent}`;
    }

    function getConfig() {
        const baseUrl = (el.baseUrl.value || "").trim().replace(/\/+$/, "");
        return {
            baseUrl,
            adminUser: el.adminUser.value || "",
            adminPass: el.adminPass.value || "",
            deviceApiKey: el.deviceApiKey.value || ""
        };
    }

    function loadConfig() {
        const isBackendOrigin = window.location.origin.includes("localhost:8080")
            || window.location.origin.includes("127.0.0.1:8080");
        const fallbackBase = isBackendOrigin ? window.location.origin : "http://localhost:8080";
        const stored = localStorage.getItem(CONFIG_KEY);
        const config = stored ? JSON.parse(stored) : {
            baseUrl: fallbackBase,
            adminUser: "admin",
            adminPass: "admin123",
            deviceApiKey: "moveinsync-device-key"
        };
        el.baseUrl.value = config.baseUrl || fallbackBase;
        el.adminUser.value = config.adminUser || "admin";
        el.adminPass.value = config.adminPass || "admin123";
        el.deviceApiKey.value = config.deviceApiKey || "moveinsync-device-key";
    }

    function saveConfig() {
        if (!el.baseUrl || !el.adminUser || !el.adminPass || !el.deviceApiKey) {
            log("ERROR", "Cannot save config: required config inputs are missing in HTML");
            return;
        }
        const config = getConfig();
        localStorage.setItem(CONFIG_KEY, JSON.stringify(config));
        log("INFO", "Connection configuration saved");
    }

    function bindIfPresent(element, eventName, handler) {
        if (!element) {
            return;
        }
        element.addEventListener(eventName, handler);
    }

    function adminAuthHeader(config) {
        return `Basic ${btoa(`${config.adminUser}:${config.adminPass}`)}`;
    }

    async function request(path, options) {
        const config = getConfig();
        if (!config.baseUrl) {
            throw new Error("Base URL is required");
        }
        const method = options.method || "GET";
        const auth = options.auth || "none";
        const headers = {
            "Content-Type": "application/json"
        };
        if (auth === "admin") {
            headers.Authorization = adminAuthHeader(config);
        }
        if (auth === "device") {
            headers["X-API-KEY"] = config.deviceApiKey;
        }

        const response = await fetch(`${config.baseUrl}${path}`, {
            method,
            headers,
            body: options.body ? JSON.stringify(options.body) : undefined
        });

        const raw = await response.text();
        let parsed = raw;
        try {
            parsed = raw ? JSON.parse(raw) : {};
        } catch (_ignored) {
            parsed = raw;
        }

        if (!response.ok) {
            const message = parsed && parsed.message ? parsed.message : response.statusText;
            throw new Error(`HTTP ${response.status}: ${message}`);
        }

        return parsed;
    }

    function setHealth(ok, label) {
        el.healthBadge.textContent = label;
        el.healthBadge.parentElement.style.background = ok ? "#e6f8f1" : "#fff3ec";
        el.healthBadge.parentElement.style.borderColor = ok ? "#b9e1d4" : "#f0c7ab";
    }

    function formDataToObject(form) {
        const data = new FormData(form);
        const out = {};
        data.forEach((value, key) => {
            if (typeof value === "string") {
                out[key] = value.trim();
            } else {
                out[key] = value;
            }
        });
        return out;
    }

    function sanitizePayload(obj) {
        const clean = {};
        Object.keys(obj).forEach((key) => {
            const value = obj[key];
            if (value === "" || value === null || value === undefined) {
                return;
            }
            clean[key] = value;
        });
        return clean;
    }

    function renderBars(container, distribution) {
        const entries = Object.entries(distribution || {});
        if (!entries.length) {
            container.innerHTML = "<p class='meta'>No data available.</p>";
            return;
        }
        const max = Math.max(...entries.map(([, count]) => count || 0), 1);
        container.innerHTML = entries.map(([label, count]) => {
            const width = Math.max(6, Math.round((count / max) * 100));
            return `
                <div class="bar-item">
                    <div class="bar-meta"><span>${label}</span><strong>${count}</strong></div>
                    <div class="bar-track"><div class="bar-fill" style="width:${width}%"></div></div>
                </div>
            `;
        }).join("");
    }

    function renderHeatmap(heatmap) {
        const regions = Object.keys(heatmap || {});
        if (!regions.length) {
            el.heatmapBody.innerHTML = "<tr><td colspan='2'>No heatmap data available.</td></tr>";
            return;
        }

        el.heatmapBody.innerHTML = regions.map((region) => {
            const versionMix = Object.entries(heatmap[region] || {})
                .map(([version, count]) => `${version}: ${count}`)
                .join(", ");
            return `
                <tr>
                    <td>${region}</td>
                    <td>${versionMix || "-"}</td>
                </tr>
            `;
        }).join("");
    }

    async function refreshHealth() {
        try {
            const res = await request("/actuator/health", { auth: "none" });
            setHealth(true, res.status || "UP");
            log("SUCCESS", "Health check succeeded", res);
        } catch (err) {
            setHealth(false, "Unavailable");
            log("ERROR", "Health check failed", { message: err.message });
        }
    }

    async function refreshDashboard() {
        try {
            const minutes = Number(el.inactiveMinutes.value || "60");
            const res = await request(`/api/v1/admin/dashboard?inactiveMinutes=${minutes}`, { auth: "admin" });
            el.metricTotal.textContent = res.totalDevices ?? 0;
            el.metricActive.textContent = res.activeDevices ?? 0;
            el.metricInactive.textContent = res.inactiveDevices ?? 0;
            el.metricSuccess.textContent = res.successfulUpdates ?? 0;
            el.metricFailure.textContent = res.failedUpdates ?? 0;
            el.metricPending.textContent = res.pendingUpdates ?? 0;
            el.metricRollout.textContent = (res.rolloutProgressPercentage ?? 0).toFixed(2);
            el.metricSuccessRate.textContent = (res.successRatePercentage ?? 0).toFixed(2);
            el.metricFailureRate.textContent = (res.failureRatePercentage ?? 0).toFixed(2);
            renderBars(el.versionBars, res.versionDistribution);
            renderBars(el.regionBars, res.regionDistribution);
            renderBars(el.groupBars, res.deviceGroupDistribution);
            renderBars(el.failureStageBars, res.failureStageDistribution);
            renderHeatmap(res.versionHeatmap);
            log("SUCCESS", "Dashboard refreshed", res);
        } catch (err) {
            log("ERROR", "Dashboard refresh failed", { message: err.message });
        }
    }

    async function refreshSchedules() {
        try {
            const res = await request("/api/v1/admin/schedules/ledger", { auth: "admin" });
            const rows = Array.isArray(res) ? res : [res];
            if (!rows.length) {
                el.schedulesBody.innerHTML = "<tr><td colspan='11'>No schedules found.</td></tr>";
                log("INFO", "No schedules available in ledger");
                return;
            }
            el.schedulesBody.innerHTML = rows.map((item) => `
                <tr>
                    <td>${item.scheduleId ?? item.id ?? "-"}</td>
                    <td>${item.fromVersion ?? "-"}</td>
                    <td>${item.toVersion ?? "-"}</td>
                    <td>${item.targetRegion ?? "-"}</td>
                    <td>${item.targetDeviceGroup ?? "-"}</td>
                    <td>${item.rolloutPercentage ?? "-"}</td>
                    <td><span class="status-badge">${item.approvalStatus ?? "-"}</span></td>
                    <td>${item.createdAt ?? item.scheduledTime ?? "-"}</td>
                    <td>${item.lastEventAction ?? "-"}</td>
                    <td>${item.lastEventAt ?? "-"}</td>
                    <td>${item.successfulDevices ?? 0}/${item.failedDevices ?? 0}</td>
                </tr>
            `).join("");
            if (el.approveScheduleForm && el.approveScheduleForm.elements.scheduleId && !el.approveScheduleForm.elements.scheduleId.value) {
                const firstScheduleId = rows[0].scheduleId ?? rows[0].id;
                if (firstScheduleId) {
                    el.approveScheduleForm.elements.scheduleId.value = String(firstScheduleId);
                }
            }
            log("SUCCESS", "Schedules refreshed", rows);
        } catch (err) {
            el.schedulesBody.innerHTML = `<tr><td colspan='11'>Schedule ledger unavailable: ${err.message}</td></tr>`;
            log("ERROR", "Schedule fetch failed", { message: err.message });
        }
    }

    async function refreshAudit() {
        try {
            const imei = (el.auditImei.value || "").trim();
            if (!imei) {
                throw new Error("IMEI is required for audit lookup");
            }
            const res = await request(`/api/v1/admin/audit/${encodeURIComponent(imei)}`, { auth: "admin" });
            const rows = Array.isArray(res) ? res : [res];
            el.auditList.innerHTML = rows.map((item) => `
                <li>
                    <strong>${item.action || "-"}</strong>
                    <div class="meta">${item.timestamp || "-"}</div>
                    <div>${item.details || "No details"}</div>
                </li>
            `).join("");
            if (!rows.length) {
                el.auditList.innerHTML = "<li>No events found for this IMEI.</li>";
            }
            log("SUCCESS", "Audit timeline loaded", rows);
        } catch (err) {
            log("ERROR", "Audit fetch failed", { message: err.message });
        }
    }

    bindIfPresent(el.saveConfigBtn, "click", saveConfig);
    bindIfPresent(el.checkHealthBtn, "click", refreshHealth);
    bindIfPresent(el.refreshDashboardBtn, "click", refreshDashboard);
    bindIfPresent(el.refreshSchedulesBtn, "click", refreshSchedules);
    bindIfPresent(el.refreshAuditBtn, "click", refreshAudit);
    bindIfPresent(el.clearConsoleBtn, "click", () => {
        if (!el.consoleOutput) {
            return;
        }
        el.consoleOutput.textContent = "";
    });

    bindIfPresent(el.createVersionForm, "submit", async (event) => {
        event.preventDefault();
        const data = formDataToObject(el.createVersionForm);
        data.mandatory = el.createVersionForm.elements.mandatory.checked;
        try {
            const res = await request("/api/v1/admin/versions", { method: "POST", auth: "admin", body: sanitizePayload(data) });
            log("SUCCESS", "Version created", res);
            el.createVersionForm.reset();
            await refreshDashboard();
        } catch (err) {
            log("ERROR", "Create version failed", { message: err.message });
        }
    });

    bindIfPresent(el.compatibilityForm, "submit", async (event) => {
        event.preventDefault();
        const data = sanitizePayload(formDataToObject(el.compatibilityForm));
        try {
            const res = await request("/api/v1/admin/compatibility", { method: "POST", auth: "admin", body: data });
            log("SUCCESS", "Compatibility rule created", res);
            await refreshSchedules();
        } catch (err) {
            log("ERROR", "Create compatibility failed", { message: err.message });
        }
    });

    bindIfPresent(el.scheduleForm, "submit", async (event) => {
        event.preventDefault();
        const data = formDataToObject(el.scheduleForm);
        data.immediate = el.scheduleForm.elements.immediate.checked;
        if (data.rolloutPercentage) {
            data.rolloutPercentage = Number(data.rolloutPercentage);
        }
        if (data.scheduledTime && !data.scheduledTime.endsWith(":00")) {
            data.scheduledTime = `${data.scheduledTime}:00`;
        }
        if (data.immediate) {
            delete data.scheduledTime;
        }
        try {
            const res = await request("/api/v1/admin/schedules", {
                method: "POST",
                auth: "admin",
                body: sanitizePayload(data)
            });
            log("SUCCESS", "Schedule created", res);
            await refreshSchedules();
            await refreshDashboard();
        } catch (err) {
            const hint = err.message.includes("FROM_VERSION_NOT_FOUND")
                ? "Hint: create the fromVersion first, or use existing versions shown in dashboard distributions."
                : undefined;
            log("ERROR", "Create schedule failed", { message: err.message, hint });
        }
    });

    bindIfPresent(el.approveScheduleForm, "submit", async (event) => {
        event.preventDefault();
        const data = formDataToObject(el.approveScheduleForm);
        const scheduleId = Number(data.scheduleId);
        if (!Number.isInteger(scheduleId) || scheduleId <= 0) {
            log("ERROR", "Approve schedule failed", { message: "Valid numeric Schedule ID is required." });
            return;
        }
        try {
            const res = await request(`/api/v1/admin/schedules/${scheduleId}/approve`, {
                method: "POST",
                auth: "admin",
                body: sanitizePayload({ comment: data.comment })
            });
            log("SUCCESS", "Schedule approved", res);
            await refreshSchedules();
            await refreshDashboard();
        } catch (err) {
            log("ERROR", "Approve schedule failed", { message: err.message });
        }
    });

    bindIfPresent(el.heartbeatForm, "submit", async (event) => {
        event.preventDefault();
        const data = sanitizePayload(formDataToObject(el.heartbeatForm));
        el.auditImei.value = data.imeiNumber || el.auditImei.value;
        try {
            const res = await request("/api/v1/device/heartbeat", {
                method: "POST",
                auth: "device",
                body: data
            });
            if (res && res.scheduleId) {
                el.statusForm.elements.scheduleId.value = String(res.scheduleId);
            }
            if (res && res.fromVersion) {
                el.statusForm.elements.fromVersion.value = res.fromVersion;
            }
            if (res && res.targetVersion) {
                el.statusForm.elements.toVersion.value = res.targetVersion;
            }
            log("SUCCESS", "Heartbeat processed", res);
            await refreshDashboard();
        } catch (err) {
            log("ERROR", "Heartbeat failed", { message: err.message });
        }
    });

    bindIfPresent(el.statusForm, "submit", async (event) => {
        event.preventDefault();
        const data = sanitizePayload(formDataToObject(el.statusForm));
        if (data.status && data.status !== "UPDATE_SCHEDULED" && !data.scheduleId) {
            log("ERROR", "Send status failed", {
                message: "Schedule ID is required for lifecycle statuses other than UPDATE_SCHEDULED."
            });
            return;
        }
        if (data.scheduleId) {
            data.scheduleId = Number(data.scheduleId);
        }
        el.auditImei.value = data.imeiNumber || el.auditImei.value;
        try {
            const res = await request("/api/v1/update/status", {
                method: "POST",
                auth: "device",
                body: data
            });
            log("SUCCESS", "Lifecycle status sent", res);
            await refreshAudit();
            await refreshSchedules();
            await refreshDashboard();
        } catch (err) {
            const hint = err.message.includes("No update session exists")
                ? "Hint: send heartbeat from an older app version that matches an active schedule, then send UPDATE_SCHEDULED first."
                : undefined;
            log("ERROR", "Send status failed", { message: err.message, hint });
        }
    });

    loadConfig();
    refreshHealth();
    refreshDashboard();
    refreshSchedules();
})();
