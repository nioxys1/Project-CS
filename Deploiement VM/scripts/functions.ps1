function Connect-ToVCenter {
    param(
        [string]$Server,
        [string]$User,
        [string]$Password
    )
    try {
        Connect-VIServer -Server $Server -User $User -Password $Password -ErrorAction Stop
        Write-Host "Connecté à $Server"
    } catch {
        Write-Error "Erreur de connexion à vCenter : $_"
        exit 1
    }
}

function Deploy-VMFromTemplate {
    param(
        [PSCustomObject]$Config
    )

    try {
        # Vérifie si le template existe
        $template = Get-VM -Name $Config.template -ErrorAction Stop

        # Clonage
        $vm = New-VM -Name $Config.vm_name `
                    -Template $template `
                    -Datastore $Config.datastore `
                    -ResourcePool (Get-Cluster $Config.cluster | Get-ResourcePool | Select-Object -First 1) `
                    -Location (Get-Folder vm) `
                    -ErrorAction Stop

        # Configuration CPU/RAM
        Set-VM -VM $vm -NumCpu $Config.cpu -MemoryMB $Config.memory -Confirm:$false

        # Configuration réseau
        Get-NetworkAdapter -VM $vm | Set-NetworkAdapter -NetworkName $Config.network -Confirm:$false

        # Démarrage de la VM
        Start-VM -VM $vm

        Write-Host "VM $($Config.vm_name) déployée avec succès."
    } catch {
        Write-Error "Erreur lors du déploiement de la VM : $_"
    }
}
