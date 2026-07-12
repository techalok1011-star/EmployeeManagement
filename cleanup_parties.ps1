# Script to import and verify party data

$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# Get login page
$lp = Invoke-WebRequest -Uri "http://localhost:8080/login" -WebSession $s -UseBasicParsing

# Extract CSRF token
$csrf = if ($lp.Content -match 'name="_csrf" value="([^"]+)"') { $matches[1] } else { "" }

# Login as admin
Invoke-WebRequest -Uri "http://localhost:8080/login" -Method Post -Body @{username='admin'; password='admin123'; _csrf=$csrf} -WebSession $s -UseBasicParsing | Out-Null

# Import parties from Excel
$imp = Invoke-RestMethod -Uri "http://localhost:8080/api/parties/import" -Method Post -WebSession $s

Write-Output "===== IMPORT RESULT ====="
Write-Output "Parties imported: $($imp.added)"

# Query all parties
$all = Invoke-RestMethod -Uri "http://localhost:8080/api/parties/suggest" -WebSession $s
Write-Output "Total in database: $($all.Count)"

Write-Output ""
Write-Output "===== ALL PARTIES (CSV FORMAT) ====="
"ID,NAME,GST,COMBINED"
$all | ForEach-Object {
    $combined = $_.combined -replace '"', '""'
    $name = $_.name -replace '"', '""'
    $gst = $_.gst -replace '"', '""'
    """$name"",""$gst"",""$combined"""
}

