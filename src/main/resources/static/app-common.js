const RONDES_TOKEN_KEY = "rondes_token";
const RONDES_ROLE_KEY = "rondes_role";
const RONDES_NAME_KEY = "rondes_name";

function getSession() {
    return {
        token: localStorage.getItem(RONDES_TOKEN_KEY),
        role: localStorage.getItem(RONDES_ROLE_KEY),
        name: localStorage.getItem(RONDES_NAME_KEY),
    };
}

function setSession(token, role, name) {
    localStorage.setItem(RONDES_TOKEN_KEY, token);
    localStorage.setItem(RONDES_ROLE_KEY, role);
    localStorage.setItem(RONDES_NAME_KEY, name);
}

function clearSession() {
    localStorage.removeItem(RONDES_TOKEN_KEY);
    localStorage.removeItem(RONDES_ROLE_KEY);
    localStorage.removeItem(RONDES_NAME_KEY);
}

async function logout() {
    try {
        await apiFetch("/api/auth/logout", { method: "POST" });
    } catch (e) {
        console.error("Erreur lors de la deconnexion serveur", e);
    } finally {
        clearSession();
        window.location.href = "login.html";
    }
}

/** Redirige vers login.html si aucune session, ou si le role n'est pas autorise sur cette page. */
function requireAuth(allowedRoles) {
    const session = getSession();
    if (!session.token) {
        window.location.href = "login.html?next=" + encodeURIComponent(window.location.pathname.split("/").pop());
        return null;
    }
    if (allowedRoles && !allowedRoles.includes(session.role)) {
        alert("Votre role (" + session.role + ") n'a pas acces a cette page.");
        window.location.href = "index.html";
        return null;
    }
    return session;
}

async function apiFetch(path, options = {}) {
    const session = getSession();
    const headers = Object.assign({}, options.headers || {});
    if (session.token) headers["Authorization"] = "Bearer " + session.token;
    if (options.body && !headers["Content-Type"]) headers["Content-Type"] = "application/json";

    const res = await fetch(path, Object.assign({}, options, { headers }));
    if (res.status === 401) {
        clearSession();
        window.location.href = "login.html";
        throw new Error("Session expiree");
    }
    if (!res.ok) {
        let message = "Erreur " + res.status;
        try {
            const body = await res.json();
            message = body.message || message;
        } catch (e) { /* pas de corps JSON */ }
        throw new Error(message);
    }
    if (res.status === 204) return null;
    return res.json();
}

function renderHeader(elementId, title) {
    const session = getSession();
    const el = document.getElementById(elementId);
    el.innerHTML = `
        <h1>${title}</h1>
        <div>
            <span class="pill">${session.name || ""} (${session.role || ""})</span>
            <button class="secondary" onclick="logout()">Deconnexion</button>
        </div>
    `;
}
