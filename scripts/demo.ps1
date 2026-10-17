Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

jav 21 -Silencioso
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $baseUrl = $env:FLASHSALE_BASE_URL
    if ([string]::IsNullOrWhiteSpace($baseUrl)) { $baseUrl = 'http://localhost:8080' }

    function Get-Json([string]$url) {
        Invoke-RestMethod -Method Get -Uri $url
    }

    function Post-Json([string]$url, [string]$cid, [hashtable]$body) {
        Invoke-RestMethod -Method Post -Uri $url -Headers @{ 'X-Correlation-ID' = $cid } -ContentType 'application/json' -Body ($body | ConvertTo-Json -Compress)
    }

    Write-Host '=== Estoque antes ==='
    Get-Json "$baseUrl/v1/products/PROD-001/stock" | ConvertTo-Json -Depth 5

    Write-Host '=== Pedido de sucesso ==='
    $sucesso = Post-Json "$baseUrl/v1/orders" 'demo-script-sucesso-0001' @{
        produto_id = 'PROD-001'; quantidade = 2; cliente_cpf = '50329291076'; cliente_nome = 'Fernando Augusto'
    }
    $pedidoSucessoId = $sucesso.pedido_id
    Start-Sleep -Seconds 2
    Get-Json "$baseUrl/v1/orders/$pedidoSucessoId" | ConvertTo-Json -Depth 5

    Write-Host '=== Estoque após sucesso ==='
    Get-Json "$baseUrl/v1/products/PROD-001/stock" | ConvertTo-Json -Depth 5

    Write-Host '=== Pedido com falha e compensação ==='
    $falha = Post-Json "$baseUrl/v1/orders" 'demo-script-falha-0002' @{
        produto_id = 'PROD-001'; quantidade = 2; cliente_cpf = '50329291076'; cliente_nome = 'Cliente FALHA Demo'
    }
    $pedidoFalhaId = $falha.pedido_id
    Start-Sleep -Seconds 3
    Get-Json "$baseUrl/v1/orders/$pedidoFalhaId" | ConvertTo-Json -Depth 5

    Write-Host '=== Estoque após compensação ==='
    Get-Json "$baseUrl/v1/products/PROD-001/stock" | ConvertTo-Json -Depth 5

    Write-Host '=== Requisição inválida (400) ==='
    & curl.exe -i -X POST "$baseUrl/v1/orders" -H 'Content-Type: application/json' -H 'X-Correlation-ID: demo-script-invalido-0003' --data '{"produto_id":"PROD-001","quantidade":0,"cliente_cpf":"50329291076","cliente_nome":"Fernando Augusto"}'

    Write-Host '=== Estoque insuficiente (409) ==='
    & curl.exe -i -X POST "$baseUrl/v1/orders" -H 'Content-Type: application/json' -H 'X-Correlation-ID: demo-script-estoque-0004' --data '{"produto_id":"PROD-002","quantidade":999,"cliente_cpf":"50329291076","cliente_nome":"Fernando Augusto"}'
} finally {
    Pop-Location
}

