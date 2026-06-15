#!/usr/bin/env bash
set -u

BASE="${BASE:-http://localhost:8080}"
COOKIE_JAR="${TMPDIR:-/tmp}/secondhand-platform-session-cookie.txt"
USERNAME="api_test_$(date +%Y%m%d%H%M%S)"
PASSWORD="test123456"
PASS=0
FAIL=0

rm -f "$COOKIE_JAR"

green='\033[0;32m'
red='\033[0;31m'
cyan='\033[0;36m'
nc='\033[0m'

record_pass() {
  echo -e "  ${green}PASS${nc} $1"
  PASS=$((PASS + 1))
}

record_fail() {
  echo -e "  ${red}FAIL${nc} $1"
  [ -n "${2:-}" ] && echo "    $2"
  FAIL=$((FAIL + 1))
}

json_code() {
  echo "$1" | grep -o '"code":[0-9]*' | head -1 | cut -d: -f2
}

request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"

  if [ -n "$body" ]; then
    curl -s -w "\n%{http_code}" -X "$method" "$BASE$path" \
      -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
      -H "Content-Type: application/json" \
      -d "$body"
  else
    curl -s -w "\n%{http_code}" -X "$method" "$BASE$path" \
      -b "$COOKIE_JAR" -c "$COOKIE_JAR"
  fi
}

expect_result() {
  local label="$1"
  local method="$2"
  local path="$3"
  local expected_code="${4:-200}"
  local body="${5:-}"
  local resp http body_text code

  resp="$(request "$method" "$path" "$body")"
  http="$(echo "$resp" | tail -1)"
  body_text="$(echo "$resp" | sed '$d')"
  code="$(json_code "$body_text")"

  if [ "$http" = "200" ] && [ "$code" = "$expected_code" ]; then
    record_pass "$label"
  else
    record_fail "$label" "http=$http code=$code body=$(echo "$body_text" | head -c 180)"
  fi
}

expect_http() {
  local label="$1"
  local method="$2"
  local path="$3"
  local expected_http="$4"
  local resp http

  resp="$(request "$method" "$path")"
  http="$(echo "$resp" | tail -1)"

  if [ "$http" = "$expected_http" ]; then
    record_pass "$label"
  else
    record_fail "$label" "http=$http"
  fi
}

echo -e "${cyan}Secondhand Platform API smoke test${nc}"
echo "BASE=$BASE"

expect_result "category list" GET "/category/list"
expect_result "product page" GET "/product/page?page=1&size=3"
expect_result "hot keywords" GET "/product/hot-keywords"
expect_http "profile requires login" GET "/user/profile" 401

user_json="{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"nickname\":\"API Test\"}"
expect_result "register" POST "/user/register" 200 "$user_json"
expect_result "login creates session" POST "/user/login" 200 "$user_json"
expect_result "profile with session" GET "/user/profile"
expect_result "pending count with session" GET "/order/pending-count"
expect_result "logout clears session" POST "/user/logout"
expect_http "profile after logout is unauthorized" GET "/user/profile" 401

docs_resp="$(request GET "/v3/api-docs")"
docs_http="$(echo "$docs_resp" | tail -1)"
docs_body="$(echo "$docs_resp" | sed '$d')"
if [ "$docs_http" = "200" ] && ! echo "$docs_body" | grep -E 'JWT|Authorization|Bearer' >/dev/null; then
  record_pass "OpenAPI uses session auth docs"
else
  record_fail "OpenAPI uses session auth docs" "http=$docs_http"
fi

echo ""
echo -e "${cyan}Result:${nc} ${green}$PASS passed${nc}, ${red}$FAIL failed${nc}"

rm -f "$COOKIE_JAR"

if [ "$FAIL" -ne 0 ]; then
  exit 1
fi
