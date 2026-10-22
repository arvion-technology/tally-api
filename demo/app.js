/**
 * Tally API, browser mirror.
 *
 * The real service is Java 17 in src/main/java: com.sun.net.httpserver,
 * a small JSON codec and an in-memory repository. This page reimplements the
 * same handlers on the sample data below so the API can be explored without
 * starting a JVM. It never calls a server.
 */

const $ = A.$;
const $$ = A.$$;
const el = A.el;
const fmt = A.fmt;
const toast = A.toast;

const CATEGORIES = ["travel", "meals", "software", "office", "training", "other"];
const STATUSES = ["submitted", "approved", "rejected"];
const TODAY = new Date().toISOString().slice(0, 10);

const SAMPLE = [
  { id: "EXP-1001", employee: "Asha Shrestha", description: "Taxi to client meeting", category: "travel", amount: 850, date: "2026-10-24", status: "approved", submittedAt: "2026-10-24T09:12:00Z", decidedAt: "2026-10-24T15:30:00Z", decisionNote: "Approved for travel" },
  { id: "EXP-1002", employee: "Asha Shrestha", description: "Team lunch", category: "meals", amount: 1250, date: "2026-10-20", status: "approved", submittedAt: "2026-10-20T11:05:00Z", decidedAt: "2026-10-21T08:40:00Z", decisionNote: "Team lunch approved" },
  { id: "EXP-1003", employee: "Bikash Tamang", description: "Editor licence", category: "software", amount: 12000, date: "2026-09-15", status: "approved", submittedAt: "2026-09-15T07:20:00Z", decidedAt: "2026-09-16T10:10:00Z", decisionNote: "Tooling" },
  { id: "EXP-1004", employee: "Sita Gurung", description: "Conference fee", category: "training", amount: 4800, date: "2026-10-03", status: "rejected", submittedAt: "2026-10-03T13:45:00Z", decidedAt: "2026-10-04T09:00:00Z", decisionNote: "Receipt missing" },
  { id: "EXP-1005", employee: "Asha Shrestha", description: "Courier to the printer", category: "other", amount: 300, date: "2026-08-11", status: "submitted", submittedAt: "2026-08-11T10:00:00Z", decidedAt: null, decisionNote: null },
  { id: "EXP-1006", employee: "Nabin Karki", description: "Hotel for site visit", category: "travel", amount: 5200, date: "2026-10-12", status: "approved", submittedAt: "2026-10-12T06:55:00Z", decidedAt: "2026-10-13T14:20:00Z", decisionNote: "Site visit" },
  { id: "EXP-1007", employee: "Rojina Maharjan", description: "Design assets", category: "software", amount: 2400, date: "2026-09-28", status: "submitted", submittedAt: "2026-09-28T08:35:00Z", decidedAt: null, decisionNote: null },
  { id: "EXP-1008", employee: "Prakash Adhikari", description: "Client dinner", category: "meals", amount: 3100, date: "2026-10-18", status: "approved", submittedAt: "2026-10-18T17:25:00Z", decidedAt: "2026-10-19T09:15:00Z", decisionNote: "Client dinner" },
  { id: "EXP-1009", employee: "Sita Gurung", description: "Taxi to airport", category: "travel", amount: 600, date: "2026-09-30", status: "rejected", submittedAt: "2026-09-30T04:40:00Z", decidedAt: "2026-10-01T08:30:00Z", decisionNote: "Duplicate claim" },
  { id: "EXP-1010", employee: "Bikash Tamang", description: "Office chair", category: "office", amount: 7200, date: "2026-10-05", status: "approved", submittedAt: "2026-10-05T12:10:00Z", decidedAt: "2026-10-06T10:05:00Z", decisionNote: "Office equipment" },
  { id: "EXP-1011", employee: "Anisha Thapa", description: "Online course", category: "training", amount: 3500, date: "2026-10-22", status: "submitted", submittedAt: "2026-10-22T09:20:00Z", decidedAt: null, decisionNote: null },
  { id: "EXP-1012", employee: "Deepak Shrestha", description: "Printer paper", category: "office", amount: 450, date: "2026-08-29", status: "approved", submittedAt: "2026-08-29T11:45:00Z", decidedAt: "2026-08-30T09:10:00Z", decisionNote: null },
];

const SAMPLE_SEQUENCE = 1013;
const clone = (value) => JSON.parse(JSON.stringify(value));

const state = {
  expenses: clone(SAMPLE),
  sequence: SAMPLE_SEQUENCE,
  selected: "summary",
  view: parseView(),
  filter: "all",
  draftBody: "",
  response: null,
};

/* ------------------------------------------------------------------ helpers -- */

function parseView() {
  return window.location.hash === "#reports" ? "reports" : "console";
}

const money = (amount) => `Rs ${fmt.num(amount)}`;
const categoryLabel = (category) => category.charAt(0).toUpperCase() + category.slice(1);
const sortedExpenses = () =>
  [...state.expenses].sort((a, b) => b.submittedAt.localeCompare(a.submittedAt) || a.id.localeCompare(b.id));
const findExpense = (id) => state.expenses.find((expense) => expense.id === id) || null;
const firstSubmitted = () => sortedExpenses().find((expense) => expense.status === "submitted") || null;
const firstExpense = () => sortedExpenses()[0] || null;

function toWire(expense) {
  return {
    id: expense.id,
    employee: expense.employee,
    description: expense.description,
    category: expense.category,
    amount: expense.amount,
    date: expense.date,
    status: expense.status,
    submittedAt: expense.submittedAt,
    decidedAt: expense.decidedAt,
    decisionNote: expense.decisionNote,
  };
}

/* --------------------------------------------------------- mirrored logic -- */

function validateDraft(body) {
  const errors = {};
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    return { body: "A JSON object is required" };
  }
  const employee = typeof body.employee === "string" ? body.employee.trim() : "";
  if (employee.length < 2 || employee.length > 80) {
    errors.employee = "employee must be between 2 and 80 characters";
  }
  const description = typeof body.description === "string" ? body.description.trim() : "";
  if (description.length < 3 || description.length > 140) {
    errors.description = "description must be between 3 and 140 characters";
  }
  if (!CATEGORIES.includes(body.category)) {
    errors.category = `category must be one of ${CATEGORIES.join(", ")}`;
  }
  const amount = Number(body.amount);
  if (!Number.isInteger(amount) || amount < 1 || amount > 1000000) {
    errors.amount = "amount must be an integer between 1 and 1000000 rupees";
  }
  if (typeof body.date !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(body.date)) {
    errors.date = "date must be formatted as YYYY-MM-DD";
  } else if (body.date > TODAY) {
    errors.date = "date cannot be in the future";
  }
  return errors;
}

function submitExpense(body) {
  const errors = validateDraft(body);
  if (Object.keys(errors).length) {
    return {
      status: 400,
      body: { error: "validation_failed", message: Object.values(errors)[0], fields: errors },
    };
  }
  const expense = {
    id: `EXP-${state.sequence++}`,
    employee: body.employee.trim(),
    description: body.description.trim(),
    category: body.category,
    amount: Number(body.amount),
    date: body.date,
    status: "submitted",
    submittedAt: new Date().toISOString(),
    decidedAt: null,
    decisionNote: null,
  };
  state.expenses.push(expense);
  return { status: 201, body: toWire(expense) };
}

function decideExpense(id, target, note) {
  const expense = findExpense(id);
  if (!expense) {
    return { status: 404, body: { error: "not_found", message: `No expense with id ${id}` } };
  }
  if (!["approved", "rejected"].includes(target)) {
    return { status: 400, body: { error: "validation_failed", message: "status must be approved or rejected", fields: { status: "status must be approved or rejected" } } };
  }
  if (expense.status !== "submitted") {
    return {
      status: 409,
      body: { error: "invalid_transition", message: `Expense ${id} is already ${expense.status}`, currentStatus: expense.status },
    };
  }
  const cleanNote = typeof note === "string" ? note.trim() : "";
  const fields = {};
  if (target === "rejected" && cleanNote.length < 3) {
    fields.note = "A rejection needs a short reason (at least 3 characters)";
  }
  if (cleanNote.length > 200) {
    fields.note = "note must be at most 200 characters";
  }
  if (Object.keys(fields).length) {
    return { status: 400, body: { error: "validation_failed", message: Object.values(fields)[0], fields } };
  }
  expense.status = target;
  expense.decidedAt = new Date().toISOString();
  expense.decisionNote = cleanNote || null;
  return { status: 200, body: toWire(expense) };
}

function monthlyReport() {
  const buckets = new Map();
  for (const expense of state.expenses) {
    if (expense.status !== "approved") continue;
    const month = expense.date.slice(0, 7);
    const entry = buckets.get(month) || { month, count: 0, total: 0 };
    entry.count += 1;
    entry.total += expense.amount;
    buckets.set(month, entry);
  }
  return [...buckets.values()].sort((a, b) => a.month.localeCompare(b.month));
}

function categoryReport() {
  const buckets = new Map();
  let total = 0;
  for (const expense of state.expenses) {
    if (expense.status !== "approved") continue;
    const entry = buckets.get(expense.category) || { category: expense.category, count: 0, total: 0, percent: 0 };
    entry.count += 1;
    entry.total += expense.amount;
    buckets.set(expense.category, entry);
    total += expense.amount;
  }
  const rows = [...buckets.values()];
  for (const row of rows) {
    row.percent = total === 0 ? 0 : Math.round((row.total * 100) / total);
  }
  return rows.sort((a, b) => b.total - a.total || a.category.localeCompare(b.category));
}

function summaryReport() {
  const counts = { submitted: 0, approved: 0, rejected: 0 };
  let approvedTotal = 0;
  let pendingTotal = 0;
  let rejectedTotal = 0;
  for (const expense of state.expenses) {
    counts[expense.status] += 1;
    if (expense.status === "approved") approvedTotal += expense.amount;
    else if (expense.status === "submitted") pendingTotal += expense.amount;
    else rejectedTotal += expense.amount;
  }
  return { total: state.expenses.length, counts, approvedTotal, pendingTotal, rejectedTotal };
}

function listResponse() {
  const items = sortedExpenses().map(toWire);
  return { status: 200, body: { items, count: items.length } };
}

function monthlyResponse() {
  const months = monthlyReport();
  return { status: 200, body: { months, count: months.length, total: months.reduce((sum, month) => sum + month.total, 0) } };
}

function categoryResponse() {
  const categories = categoryReport();
  return { status: 200, body: { categories, count: categories.length, total: categories.reduce((sum, row) => sum + row.total, 0) } };
}

function summaryResponse() {
  const summary = summaryReport();
  return {
    status: 200,
    body: {
      total: summary.total,
      counts: summary.counts,
      approvedTotal: summary.approvedTotal,
      pendingTotal: summary.pendingTotal,
    },
  };
}

function healthResponse() {
  return { status: 200, body: { status: "ok", service: "tally", java: "mirror of the JDK 17 service" } };
}

/* ---------------------------------------------------------------- endpoints -- */

const endpoints = [
  { id: "list", group: "Expenses", method: "GET", display: "/expenses", path: () => "/expenses", run: () => listResponse() },
  {
    id: "create",
    group: "Expenses",
    method: "POST",
    display: "/expenses",
    path: () => "/expenses",
    sampleBody: () =>
      JSON.stringify({ employee: "Anisha Thapa", description: "Client lunch at Boudha", category: "meals", amount: 1450, date: TODAY }, null, 2),
    run: (body) => submitExpense(body),
  },
  {
    id: "get",
    group: "Expenses",
    method: "GET",
    display: "/expenses/:id",
    path: () => `/expenses/${firstExpense()?.id || "EXP-1001"}`,
    run: () => {
      const expense = firstExpense();
      return expense ? { status: 200, body: toWire(expense) } : { status: 404, body: { error: "not_found", message: "No expenses yet" } };
    },
  },
  {
    id: "status",
    group: "Expenses",
    method: "POST",
    display: "/expenses/:id/status",
    path: () => `/expenses/${firstSubmitted()?.id || "EXP-1005"}/status`,
    sampleBody: () => JSON.stringify({ status: "approved", note: "Approved in the demo" }, null, 2),
    run: (body, path) => {
      const id = path.split("/")[2] || "";
      if (!body || typeof body !== "object") {
        return { status: 400, body: { error: "invalid_json", message: "A JSON request body is required" } };
      }
      return decideExpense(id, body.status, body.note);
    },
  },
  { id: "monthly", group: "Reports", method: "GET", display: "/reports/monthly", path: () => "/reports/monthly", run: () => monthlyResponse() },
  { id: "categories", group: "Reports", method: "GET", display: "/reports/categories", path: () => "/reports/categories", run: () => categoryResponse() },
  { id: "summary", group: "Reports", method: "GET", display: "/reports/summary", path: () => "/reports/summary", run: () => summaryResponse() },
  { id: "health", group: "Service", method: "GET", display: "/health", path: () => "/health", run: () => healthResponse() },
];

function findEndpoint(id) {
  return endpoints.find((endpoint) => endpoint.id === id) || endpoints[0];
}

function statusLabel(status) {
  return { 200: "200 OK", 201: "201 Created", 400: "400 Bad Request", 404: "404 Not Found", 409: "409 Conflict" }[status] || "No response";
}

function statusTone(status) {
  if (status >= 500) return "warn";
  if (status >= 400) return "error";
  return "ok";
}

function execute(endpointId, rawBody) {
  const endpoint = findEndpoint(endpointId);
  const path = endpoint.path();
  let requestBody = null;
  let result;
  if (endpoint.method === "POST") {
    requestBody = rawBody == null ? endpoint.sampleBody() : rawBody;
    let body;
    try {
      body = JSON.parse(requestBody);
    } catch {
      result = { status: 400, body: { error: "invalid_json", message: "Request body is not valid JSON" } };
    }
    if (!result) result = endpoint.run(body, path);
    state.draftBody = requestBody;
  } else {
    result = endpoint.run(path);
  }
  state.selected = endpoint.id;
  state.response = { method: endpoint.method, path, status: result.status, requestBody, body: result.body };
  return state.response;
}

/* -------------------------------------------------------------------- view -- */

function endpointButton(endpoint) {
  return el(
    "button",
    { class: `endpoint${state.selected === endpoint.id ? " active" : ""}`, type: "button", "data-endpoint": endpoint.id },
    el("span", { class: `method ${endpoint.method.toLowerCase()}`, text: endpoint.method }),
    el("span", { class: "endpoint-path", text: endpoint.display })
  );
}

function renderEndpointNav() {
  const nav = $("#endpoint-nav");
  nav.replaceChildren();
  for (const group of ["Expenses", "Reports", "Service"]) {
    nav.append(el("div", { class: "nav-label", text: group }));
    for (const endpoint of endpoints.filter((item) => item.group === group)) {
      nav.append(endpointButton(endpoint));
    }
  }
}

function statCard(label, value, sub, tone = "") {
  return el(
    "div",
    { class: `card api-stat ${tone}` },
    el("div", { class: "stat-label", text: label }),
    el("div", { class: "stat-value", text: value }),
    el("div", { class: "stat-sub", text: sub })
  );
}

function pageHead(title, lede, note) {
  return el(
    "div",
    { class: "page-head api-head" },
    el("div", {}, el("div", { class: "eyebrow", text: "Backend / API" }), el("h1", { text: title }), el("p", { class: "lede", text: lede })),
    el("span", { class: "spacer" }),
    el("div", { class: "mirror-note" }, el("span", { class: "dot" }), el("span", { text: note }))
  );
}

function consoleView() {
  const summary = summaryReport();
  const response = state.response;
  const endpoint = findEndpoint(state.selected);
  const filtered = sortAndFilter();

  const requestLine = el(
    "div",
    { class: "request-line" },
    el("span", { class: `method ${endpoint.method.toLowerCase()}`, text: endpoint.method }),
    el("code", { class: "request-path", text: response ? response.path : endpoint.path() })
  );

  const requestPane = el(
    "div",
    { class: "request-pane" },
    requestLine,
    endpoint.method === "POST"
      ? el("textarea", { class: "request-body", id: "request-body", spellcheck: "false", "aria-label": "Request body" }, state.draftBody || endpoint.sampleBody())
      : el("div", { class: "request-empty", text: "GET requests have no body. The path and query carry the input." }),
    el(
      "div",
      { class: "request-actions" },
      el("button", { class: "btn primary sm", type: "button", "data-action": "send", text: "Send request" }),
      el("span", { class: "small muted", text: "Runs in this page. Nothing leaves the browser." })
    )
  );

  const responsePane = el(
    "div",
    { class: "response-pane" },
    el(
      "div",
      { class: "response-head" },
      el("h2", { text: "Response" }),
      el("span", { class: `status-code ${response ? statusTone(response.status) : ""}`, text: response ? statusLabel(response.status) : "Not sent" }),
      el("span", { class: "spacer" }),
      el("span", { class: "mono small muted", text: "application/json" })
    ),
    response
      ? el("pre", { class: "code response-code", text: JSON.stringify(response.body, null, 2) })
      : el("div", { class: "response-empty", text: "Press Send request to run the handler." })
  );

  const consoleCard = el(
    "div",
    { class: "card console-card" },
    el(
      "div",
      { class: "card-head-row" },
      el("h2", { text: "Request and response" }),
      el("span", { class: "sub", text: "mirrored handler" }),
      el("span", { class: "spacer" }),
      el("button", { class: "btn tiny", type: "button", "data-action": "copy-curl", text: "Copy curl" })
    ),
    el("div", { class: "console-split" }, requestPane, responsePane)
  );

  const months = monthlyReport();
  const categories = categoryReport();
  const chartHost = el("div", { class: "snapshot-chart" });
  chartHost.append(
    A.lineChart({
      series: [{ data: months.map((month) => month.total) }],
      labels: months.map((month) => monthLabel(month.month)),
      height: 110,
      width: 380,
      yFormat: (value) => `Rs ${fmt.compact(value)}`,
    })
  );
  const categoryBars = A.barList({
    rows: categories.slice(0, 3).map((row) => ({ label: categoryLabel(row.category), value: row.total })),
    format: (value) => money(value),
  });

  const snapshotCard = el(
    "div",
    { class: "card snapshot-card" },
    el("div", { class: "card-head-row" }, el("h2", { text: "Snapshot" }), el("span", { class: "sub", text: "approved only" })),
    el("div", { class: "snapshot-total" }, el("span", { text: "Approved spend" }), el("strong", { text: money(summary.approvedTotal) })),
    chartHost,
    el("div", { class: "snapshot-categories" }, categoryBars),
    el("button", { class: "card-link", type: "button", "data-view": "reports", text: "Open reports →" })
  );

  const table = el(
    "div",
    { class: "card expenses-card" },
    el(
      "div",
      { class: "card-head-row" },
      el("h2", { text: "Expenses" }),
      el("span", { class: "sub", text: "newest first" }),
      el("span", { class: "spacer" }),
      el("span", { class: "count-label", text: `${filtered.length} shown` }),
      statusFilter()
    ),
    el(
      "div",
      { class: "table-wrap expense-wrap" },
      el(
        "table",
        { class: "table expense-table" },
        el("thead", {}, el("tr", {}, ...["ID", "Claim", "Category", "Date", "Amount", "Status", "Actions"].map((label) => el("th", { text: label })))),
        el("tbody", {}, ...(filtered.length ? filtered.map(expenseRow) : [el("tr", {}, el("td", { colspan: "7", class: "muted center", text: "No expenses match this filter." }))]))
      )
    )
  );

  return [
    pageHead(
      "Tally",
      "A small expense approval API. The real service is Java 17 on com.sun.net.httpserver; this page mirrors its validation, status transitions and reports in the browser.",
      "static mirror, no server attached"
    ),
    el(
      "div",
      { class: "stat-grid api-stats" },
      statCard("Approved spend", money(summary.approvedTotal), `${summary.counts.approved} approved claims`),
      statCard("Pending review", money(summary.pendingTotal), `${summary.counts.submitted} submitted claims`),
      statCard("Rejected", String(summary.counts.rejected), `${money(summary.rejectedTotal)} returned`),
      statCard("Expenses", String(summary.total), `${categories.length} categories with approved spend`)
    ),
    el("div", { class: "console-grid" }, consoleCard, snapshotCard),
    table,
  ];
}

function statusFilter() {
  return el(
    "select",
    { class: "select filter-select", id: "expense-filter", "aria-label": "Filter expenses by status" },
    el("option", { value: "all", selected: state.filter === "all" }, "All statuses"),
    ...STATUSES.map((status) => el("option", { value: status, selected: state.filter === status }, statusLabelFor(status)))
  );
}

function statusLabelFor(status) {
  return status.charAt(0).toUpperCase() + status.slice(1);
}

function sortAndFilter() {
  return sortedExpenses().filter((expense) => state.filter === "all" || expense.status === state.filter);
}

function expenseRow(expense) {
  const actions = el("div", { class: "row-actions" });
  if (expense.status === "submitted") {
    actions.append(
      el("button", { class: "btn tiny primary", type: "button", "data-approve": expense.id, text: "Approve" }),
      el("button", { class: "btn tiny danger", type: "button", "data-reject": expense.id, text: "Reject" })
    );
  } else {
    actions.append(el("span", { class: "closed-note", text: "decided" }));
  }
  return el(
    "tr",
    { "data-expense-row": expense.id },
    el("td", {}, el("span", { class: "mono id-cell", text: expense.id })),
    el("td", {}, el("div", { class: "claim-desc", text: expense.description }), el("div", { class: "claim-employee", text: expense.employee })),
    el("td", {}, el("span", { class: `cat-pill cat-${expense.category}`, text: categoryLabel(expense.category) })),
    el("td", {}, el("span", { class: "date-cell", text: expense.date })),
    el("td", {}, el("span", { class: "amount-cell", text: money(expense.amount) })),
    el("td", {}, el("span", { class: `status-pill status-${expense.status}`, text: statusLabelFor(expense.status) })),
    el("td", {}, actions)
  );
}

function reportsView() {
  const summary = summaryReport();
  const months = monthlyReport();
  const categories = categoryReport();
  const average = summary.counts.approved ? Math.round(summary.approvedTotal / summary.counts.approved) : 0;

  const chartHost = el("div", { class: "report-chart" });
  chartHost.append(
    A.lineChart({
      series: [{ data: months.map((month) => month.total) }],
      labels: months.map((month) => monthLabel(month.month)),
      height: 220,
      width: 760,
      yFormat: (value) => `Rs ${fmt.compact(value)}`,
    })
  );

  const categoryBars = A.barList({
    rows: categories.map((row) => ({ label: `${categoryLabel(row.category)} (${row.count})`, value: row.total })),
    format: (value) => money(value),
  });

  const monthlyTable = el(
    "table",
    { class: "table" },
    el("thead", {}, el("tr", {}, ...["Month", "Approved claims", "Total"].map((label) => el("th", { text: label })))),
    el(
      "tbody",
      {},
      ...months.map((month) =>
        el("tr", {}, el("td", {}, el("span", { class: "mono", text: month.month })), el("td", { class: "num", text: String(month.count) }), el("td", { class: "num", text: money(month.total) }))
      )
    )
  );

  return [
    pageHead(
      "Reports",
      "Monthly and category reports count approved expenses only. Submitted claims are pending, rejected claims never become spend.",
      "GET /reports/monthly"
    ),
    el(
      "div",
      { class: "stat-grid api-stats" },
      statCard("Approved total", money(summary.approvedTotal), `${summary.counts.approved} approved claims`),
      statCard("Months on record", String(months.length), "with approved spend"),
      statCard("Average claim", money(average), "approved claims only"),
      statCard("Categories", String(categories.length), "with approved spend")
    ),
    el(
      "div",
      { class: "report-grid" },
      el(
        "div",
        { class: "card report-chart-card" },
        el(
          "div",
          { class: "card-head-row" },
          el("h2", { text: "Approved spend by month" }),
          el("span", { class: "method get", text: "GET" }),
          el("code", { class: "request-path", text: "/reports/monthly" }),
          el("span", { class: "spacer" }),
          el("button", { class: "btn tiny", type: "button", "data-endpoint": "monthly", text: "Run endpoint" })
        ),
        chartHost
      ),
      el(
        "div",
        { class: "card report-side-card" },
        el("div", { class: "card-head-row" }, el("h2", { text: "By category" }), el("span", { class: "sub", text: "approved only" })),
        categoryBars,
        el("div", { class: "promo-hint", text: "Percentages are rounded to whole numbers. The API returns the same totals." })
      )
    ),
    el(
      "div",
      { class: "report-grid lower" },
      el(
        "div",
        { class: "card" },
        el("div", { class: "card-head-row" }, el("h2", { text: "Monthly totals" }), el("span", { class: "spacer" }), el("span", { class: "sub", text: "oldest first" })),
        months.length ? monthlyTable : el("div", { class: "muted small", text: "No approved expenses yet." })
      ),
      el(
        "div",
        { class: "card" },
        el("div", { class: "card-head-row" }, el("h2", { text: "Raw response" }), el("span", { class: "mono small muted", text: "GET /reports/monthly" })),
        el("pre", { class: "code report-raw", text: JSON.stringify(monthlyResponse().body, null, 2) })
      )
    ),
  ];
}

function monthLabel(month) {
  const [year, number] = month.split("-");
  const names = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  return `${names[Number(number) - 1]} ${year.slice(2)}`;
}

/* ------------------------------------------------------------------ render -- */

function render() {
  renderEndpointNav();
  for (const tab of $$("#view-tabs .tab")) {
    tab.classList.toggle("active", tab.dataset.view === state.view);
  }
  const main = $("#main");
  main.replaceChildren(...(state.view === "reports" ? reportsView() : consoleView()));
  document.title = state.view === "reports" ? "Reports | Tally API" : "Console | Tally API";
}

function setView(view) {
  state.view = view;
  if (window.location.hash !== `#${view}`) {
    window.history.replaceState(null, "", `#${view}`);
  }
  render();
  window.scrollTo(0, 0);
}

function selectEndpoint(id, options = {}) {
  const endpoint = findEndpoint(id);
  state.view = "console";
  state.selected = id;
  if (window.location.hash !== "#console") {
    window.history.replaceState(null, "", "#console");
  }
  if (endpoint.method === "POST") {
    state.draftBody = endpoint.sampleBody();
    state.response = null;
    render();
    return;
  }
  execute(id);
  render();
  if (options.scroll !== false) window.scrollTo(0, 0);
}

function sendCurrent() {
  const bodyField = $("#request-body");
  execute(state.selected, bodyField ? bodyField.value : undefined);
  render();
  const response = state.response;
  toast(response && response.status < 400 ? "Request mirrored successfully" : "The handler returned an error", response && response.status < 400 ? "success" : "error", 1400);
}

function approveExpense(id) {
  const result = decideExpense(id, "approved", "Approved in the demo");
  state.selected = "status";
  state.response = {
    method: "POST",
    path: `/expenses/${id}/status`,
    status: result.status,
    requestBody: JSON.stringify({ status: "approved", note: "Approved in the demo" }, null, 2),
    body: result.body,
  };
  render();
  toast(result.status === 200 ? `${id} approved` : "That transition was refused", result.status === 200 ? "success" : "error", 1600);
}

function rejectExpense(id) {
  const result = decideExpense(id, "rejected", "Receipt missing");
  state.selected = "status";
  state.response = {
    method: "POST",
    path: `/expenses/${id}/status`,
    status: result.status,
    requestBody: JSON.stringify({ status: "rejected", note: "Receipt missing" }, null, 2),
    body: result.body,
  };
  render();
  toast(result.status === 200 ? `${id} rejected` : "That transition was refused", result.status === 200 ? "success" : "error", 1600);
}

function resetDemo() {
  state.expenses = clone(SAMPLE);
  state.sequence = SAMPLE_SEQUENCE;
  state.filter = "all";
  state.selected = "summary";
  state.view = "console";
  state.draftBody = "";
  window.history.replaceState(null, "", "#console");
  execute("summary");
  render();
  toast("Sample data reset", "success", 1500);
}

function copyCurl() {
  const response = state.response;
  const endpoint = findEndpoint(state.selected);
  const path = response ? response.path : endpoint.path();
  const body = response && response.requestBody ? response.requestBody : endpoint.sampleBody ? endpoint.sampleBody() : null;
  let command = `curl -s http://127.0.0.1:8080${path}`;
  if (endpoint.method === "POST") {
    command += " -X POST -H 'Content-Type: application/json'";
    command += ` -d '${String(body).replace(/\n\s*/g, "")}'`;
  }
  navigator.clipboard
    .writeText(command)
    .then(() => toast("curl copied", "success", 1400))
    .catch(() => toast("Could not copy. Select it from the request panel instead.", "error", 2200));
}

/* ------------------------------------------------------------------ events -- */

document.addEventListener("click", (event) => {
  const target = event.target;
  const endpoint = target.closest("[data-endpoint]");
  if (endpoint) {
    selectEndpoint(endpoint.dataset.endpoint);
    return;
  }
  const view = target.closest("[data-view]");
  if (view) {
    setView(view.dataset.view);
    return;
  }
  const approval = target.closest("[data-approve]");
  if (approval) {
    approveExpense(approval.dataset.approve);
    return;
  }
  const rejection = target.closest("[data-reject]");
  if (rejection) {
    rejectExpense(rejection.dataset.reject);
    return;
  }
  const action = target.closest("[data-action]");
  if (!action) return;
  switch (action.dataset.action) {
    case "send":
      sendCurrent();
      break;
    case "copy-curl":
      copyCurl();
      break;
    case "reset":
      resetDemo();
      break;
    default:
      break;
  }
});

document.addEventListener("input", (event) => {
  if (event.target.id === "request-body") {
    state.draftBody = event.target.value;
  }
});

document.addEventListener("change", (event) => {
  if (event.target.id === "expense-filter") {
    state.filter = event.target.value;
    render();
  }
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && (event.metaKey || event.ctrlKey) && event.target.id === "request-body") {
    event.preventDefault();
    sendCurrent();
  }
});

window.addEventListener("hashchange", () => {
  const view = parseView();
  if (view !== state.view) {
    state.view = view;
    render();
  }
});

/* --------------------------------------------------------------- automation -- */

window.tally = {
  state: () => ({
    view: state.view,
    selected: state.selected,
    status: state.response?.status ?? null,
    expenses: state.expenses.length,
    summary: summaryReport(),
  }),
  choose: (id) => selectEndpoint(id),
  send: () => {
    sendCurrent();
    return state.response?.status ?? null;
  },
  approve: (id) => {
    approveExpense(id);
    return state.expenses.find((expense) => expense.id === id)?.status;
  },
  reject: (id) => {
    rejectExpense(id);
    return state.expenses.find((expense) => expense.id === id)?.status;
  },
  view: (name) => setView(name),
  reset: () => resetDemo(),
  monthly: () => monthlyReport(),
  categories: () => categoryReport(),
  firstSubmitted: () => firstSubmitted()?.id ?? null,
};

/* ------------------------------------------------------------------- start -- */

execute("summary");
render();
