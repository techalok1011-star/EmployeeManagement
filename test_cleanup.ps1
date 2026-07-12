# Complete cleanup workflow

$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# Login
$lp = Invoke-WebRequest -Uri "http://localhost:8080/login" -WebSession $s -UseBasicParsing -ErrorAction Stop
$csrf = ([regex]::Match($lp.Content, 'name="_csrf" value="([^"]+)"')).Groups[1].Value
Invoke-WebRequest -Uri "http://localhost:8080/login" -Method Post -Body @{username='admin'; password='admin123'; _csrf=$csrf} -WebSession $s -UseBasicParsing | Out-Null

# Before cleanup
$before = @(Invoke-RestMethod -Uri "http://localhost:8080/api/parties/suggest" -WebSession $s)
"BEFORE CLEANUP:"
"Total entries: $($before.Count)"
""

# Run cleanup
$cl = Invoke-RestMethod -Uri "http://localhost:8080/api/parties/cleanup" -Method Post -WebSession $s
"Cleanup Result: Deleted $($cl.deleted) invalid entries"
""

# After cleanup
Start-Sleep -Seconds 1
$after = @(Invoke-RestMethod -Uri "http://localhost:8080/api/parties/suggest?limit=30" -WebSession $s)
"AFTER CLEANUP:"
"Total entries: $($after.Count)"
"First 5 entries:"
$after[0..4] | ForEach-Object { " • $($_.combined)" }

