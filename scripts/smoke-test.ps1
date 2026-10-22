# Smoke test for the full stack through the API Gateway (http://localhost:8080)
# Requires: docker compose up (gateway, keycloak, order-service, inventory-service)

$GatewayBase = "http://localhost:8080"
$KeycloakBase = "http://localhost:8180"
$Realm = "orders-realm"
$ClientId = "postman-client"
$Username = "testuser"
$Password = "test1234"

$KeyboardId = "11111111-1111-4111-8b2d-111111111111" # SKU-001 (stock 100)
$PassCount = 0
$FailCount = 0

function Result($Name, $Ok, $Detail) {
    if ($Ok) {
        Write-Host ("OK   - {0}" -f $Name) -ForegroundColor Green
        $script:PassCount++
    }
    else {
        Write-Host ("FAIL - {0} :: {1}" -f $Name, $Detail) -ForegroundColor Red
        $script:FailCount++
    }
}

# Raw HTTP helper on top of HttpClient: PS 5.1 hides error bodies from Invoke-RestMethod,
# and HttpClient gives status + body + single-value header access for every response.
Add-Type -AssemblyName System.Net.Http
$script:http = New-Object System.Net.Http.HttpClient
$script:http.Timeout = [TimeSpan]::FromSeconds(60)

function Invoke-Http($Verb, $Uri, $Headers, $Body) {
    $method = New-Object System.Net.Http.HttpMethod($Verb)
    $request = New-Object System.Net.Http.HttpRequestMessage($method, $Uri)
    if ($Headers) {
        foreach ($key in $Headers.Keys) {
            $null = $request.Headers.TryAddWithoutValidation($key, [string]$Headers[$key])
        }
    }
    if ($Body) {
        $content = New-Object System.Net.Http.StringContent($Body, [System.Text.Encoding]::UTF8, "application/json")
        $request.Content = $content
    }
    $response = $script:http.SendAsync($request).GetAwaiter().GetResult()
    $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $traceValue = $null
    $script:traceHeaders = $null
    if ($response.Headers.TryGetValues("X-Trace-Id", [ref]$script:traceHeaders)) {
        $traceValue = ($script:traceHeaders | Select-Object -First 1)
    }
    $parsed = $null
    try { $parsed = $text | ConvertFrom-Json } catch { }
    return @{ StatusCode = [int]$response.StatusCode; Body = $parsed; BodyText = $text; TraceHeader = $traceValue }
}

# Runs a request where we *expect* an error and returns @{ StatusCode; Body }
function Invoke-ExpectError($Method, $Uri, $Headers, $Body) {
    return Invoke-Http $Method $Uri $Headers $Body
}

Write-Host "=== Smoke test against $GatewayBase ==="
Write-Host ""

# 1. Request a token from Keycloak
$token = $null
try {
    $tokenResp = Invoke-RestMethod -Method Post `
        -Uri "$KeycloakBase/realms/$Realm/protocol/openid-connect/token" `
        -Body @{ grant_type = "password"; client_id = $ClientId; username = $Username; password = $Password } `
        -TimeoutSec 30
    $token = $tokenResp.access_token
} catch { }
Result "Keycloak token (postman-client/testuser)" ($null -ne $token -and $token.Length -gt 20) "no token received"

if (-not $token) {
    Write-Host ""
    Write-Host ("Summary: {0} OK, {1} FAIL" -f $PassCount, $FailCount)
    exit 1
}

$authHeaders = @{ Authorization = "Bearer $token" }

# 2. Create order (201 + CONFIRMED). Body built with ConvertTo-Json, no escaped quotes.
$orderBody = @{
    customerId = "customer-smoke-001"
    items = @(
        @{ productId = $KeyboardId; quantity = 2; unitPrice = 89.90 }
    )
} | ConvertTo-Json -Depth 5

$created = Invoke-Http "POST" "$GatewayBase/api/orders" $authHeaders $orderBody
$ok = $created.StatusCode -eq 201 -and $null -ne $created.Body -and $created.Body.status -eq "CONFIRMED" -and $null -ne $created.Body.id
Result "Create order (201 + CONFIRMED)" $ok ("http=$($created.StatusCode) status=$($created.Body.status) body=$($created.BodyText)")
$orderId = $created.Body.id

if (-not $orderId) {
    Write-Host ""
    Write-Host ("Summary: {0} OK, {1} FAIL" -f $PassCount, $FailCount)
    exit 1
}

# 3. Get order by id
$fetched = Invoke-Http "GET" "$GatewayBase/api/orders/$orderId" $authHeaders $null
Result "Get order by id" ($fetched.StatusCode -eq 200 -and $fetched.Body.id -eq $orderId -and $fetched.Body.status -eq "CONFIRMED") ("http=$($fetched.StatusCode) id=$($fetched.Body.id) status=$($fetched.Body.status)")

# 4. Get history (2 rows: null->PENDING, PENDING->CONFIRMED)
$history = Invoke-Http "GET" "$GatewayBase/api/orders/$orderId/history" $authHeaders $null
$rows = 0
if ($history.Body) {
    if ($history.Body -is [array]) { $rows = @($history.Body).Count } else { $rows = 1 }
}
Result "Get history (2 rows)" ($history.StatusCode -eq 200 -and $rows -eq 2) ("http=$($history.StatusCode) rows=$rows")

# 5. Cancel order (200 + CANCELLED)
$cancelled = Invoke-Http "POST" "$GatewayBase/api/orders/$orderId/cancel" $authHeaders $null
Result "Cancel order (200 + CANCELLED)" ($cancelled.StatusCode -eq 200 -and $cancelled.Body.status -eq "CANCELLED") ("http=$($cancelled.StatusCode) status=$($cancelled.Body.status)")

# 6. Cancel again (409 INVALID_STATE_TRANSITION)
$err = Invoke-ExpectError "POST" "$GatewayBase/api/orders/$orderId/cancel" $authHeaders $null
Result "Cancel again (409 INVALID_STATE_TRANSITION)" ($err.StatusCode -eq 409 -and $err.Body.code -eq "INVALID_STATE_TRANSITION") "http=$($err.StatusCode) code=$($err.Body.code)"

# 7. Insufficient stock (409 STOCK_INSUFFICIENT)
$bigBody = @{
    customerId = "customer-smoke-001"
    items = @(
        @{ productId = $KeyboardId; quantity = 10000; unitPrice = 89.90 }
    )
} | ConvertTo-Json -Depth 5
$err = Invoke-ExpectError "POST" "$GatewayBase/api/orders" $authHeaders $bigBody
Result "Insufficient stock (409 STOCK_INSUFFICIENT)" ($err.StatusCode -eq 409 -and $err.Body.code -eq "STOCK_INSUFFICIENT") "http=$($err.StatusCode) code=$($err.Body.code)"

# 8. Nonexistent order (404 ORDER_NOT_FOUND)
$err = Invoke-ExpectError "GET" "$GatewayBase/api/orders/00000000-0000-4000-8000-000000000000" $authHeaders $null
Result "Nonexistent order (404 ORDER_NOT_FOUND)" ($err.StatusCode -eq 404 -and $err.Body.code -eq "ORDER_NOT_FOUND") "http=$($err.StatusCode) code=$($err.Body.code)"

# 9. Request without token (401 INVALID_TOKEN)
$err = Invoke-ExpectError "GET" "$GatewayBase/api/orders/$orderId" $null $null
Result "No token (401 INVALID_TOKEN)" ($err.StatusCode -eq 401 -and $err.Body.code -eq "INVALID_TOKEN") "http=$($err.StatusCode) code=$($err.Body.code)"

# 10. Fixed X-Trace-Id is echoed back unchanged
$fixedTrace = "trace-smoke-" + [Guid]::NewGuid().ToString("N")
$traceHeaders = @{ Authorization = "Bearer $token"; "X-Trace-Id" = $fixedTrace }
$traceBody = @{
    customerId = "customer-smoke-001"
    items = @(
        @{ productId = $KeyboardId; quantity = 1; unitPrice = 89.90 }
    )
} | ConvertTo-Json -Depth 5
$traceResp = Invoke-Http "POST" "$GatewayBase/api/orders" $traceHeaders $traceBody
$echo = $traceResp.TraceHeader
Result "Fixed X-Trace-Id echoed back" ($echo -eq $fixedTrace) "expected=$fixedTrace got=$echo"

Write-Host ""
Write-Host ("Summary: {0} OK, {1} FAIL" -f $PassCount, $FailCount)
if ($FailCount -gt 0) { exit 1 }
exit 0
