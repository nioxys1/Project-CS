#!/bin/bash
# Initialise le master node

set -e

bash $(dirname "$0")/common-setup.sh

sudo kubeadm init --pod-network-cidr=10.244.0.0/16

mkdir -p $HOME/.kube
sudo cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
sudo chown $(id -u):$(id -g) $HOME/.kube/config

# Appliquer le CNI flannel
kubectl apply -f https://raw.githubusercontent.com/coreos/flannel/master/Documentation/kube-flannel.yml

# Générer la commande join
kubeadm token create --print-join-command > /tmp/join.sh
chmod +x /tmp/join.sh
echo "Commande join générée dans /tmp/join.sh"
