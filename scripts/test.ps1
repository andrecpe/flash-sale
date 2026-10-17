Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

jav 21 -Silencioso
Push-Location (Join-Path $PSScriptRoot '..')
try {
    .\mvnw.cmd -q verify
} finally {
    Pop-Location
}

