# deploy-vm.ps1
Import-Module VMware.PowerCLI
Import-Module "$PSScriptRoot/functions.ps1"

# Lire la config JSON
$configPath = "$PSScriptRoot/../config/vm-config.json"
$config = Get-Content $configPath | ConvertFrom-Json

# Connexion
Connect-ToVCenter -Server $config.vcenter -User $config.username -Password $config.password

# Déploiement
Deploy-VMFromTemplate -Config $config

# Déconnexion
Disconnect-VIServer -Server $config.vcenter -Confirm:$false
