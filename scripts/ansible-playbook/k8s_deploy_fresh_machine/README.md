# Install k8s with Ansible use root user on ubuntu 18.04


### Install python3 and etc...
`apt update` 

`sudo apt install python3-dev python3-venv libffi-dev gcc libssl-dev git`

### Install ansible with python3 on one machine
`pip install 'ansible<=2.11'`

### Reference the bellow link, but need to make minor adjustments
https://www.digitalocean.com/community/tutorials/how-to-create-a-kubernetes-cluster-using-kubeadm-on-ubuntu-18-04

___

## Step 1: prepare ansible and setup inventory (list of machines)

```
mkdir ~/kube-cluster
cd ~/kube-cluster
vim ~/kube-cluster/hosts
```

```
[masters]
master ansible_host=master_ip ansible_user=root

[workers]
worker1 ansible_host=worker_1_ip ansible_user=root
worker2 ansible_host=worker_2_ip ansible_user=root

[all:vars]
ansible_python_interpreter=/usr/bin/python3
```

___

## Step 2: Install K8s dependencies (include Docker) 

`vim ~/kube-cluster/kube-dependencies.yml`

```
- hosts: all
  become: yes
  tasks:
  - name: install Docker
    apt:
      name: docker.io
      state: present
      update_cache: true

  - name: install APT Transport HTTPS
    apt:
      name: apt-transport-https
      state: present

  - name: add Kubernetes apt-key
    apt_key:
      url: https://packages.cloud.google.com/apt/doc/apt-key.gpg
      state: present

  - name: add Kubernetes' APT repository
    apt_repository:
      repo: deb http://apt.kubernetes.io/ kubernetes-xenial main
      state: present
      filename: 'kubernetes'

  - name: install kubelet
    apt:
      name: kubelet=1.19.4-00
      state: present
      update_cache: true

  - name: install kubeadm
    apt:
      name: kubeadm=1.19.4-00
      state: present

- hosts: master
  become: yes
  tasks:
  - name: install kubectl
    apt:
    name: kubectl=1.19.4-00
    state: present
    force: yes
```

### Run the above ansilbe playbook, with the host folder setuped earlier
`ansible-playbook -i hosts ~/kube-cluster/kube-dependencies.yml`

___

## Step 3: Set up Master Node
`vim ~/kube-cluster/master.yml` 

```
- hosts: master
  become: yes
  tasks:
  - name: initialize the cluster
    shell: kubeadm init --pod-network-cidr=10.244.0.0/16 >> cluster_initialized.txt
    args:
      chdir: $HOME
      creates: cluster_initialized.txt

  - name: install Pod network, flannel
    environment:
      KUBECONFIG: /etc/kubernetes/admin.conf
    become: yes
    shell: kubectl apply -f https://raw.githubusercontent.com/flannel-io/flannel/master/Documentation/kube-flannel.yml >> pod_network_setup.txt
    args:
      chdir: $HOME
      creates: pod_network_setup.txt
```

### Run the above ansilbe playbook, with the host folder setuped earlier
`ansible-playbook -i hosts ~/kube-cluster/master.yml` 

### To check the installation of k8s master node

```
kubectl get nodes
kubectl get pods -A
```

___

## Step 4: Setup Worker Nodes

`vim ~/kube-cluster/workers.yml`

```
- hosts: master
  become: yes
  gather_facts: false
  tasks:
  - name: get join command
    environment:
      KUBECONFIG: /etc/kubernetes/admin.conf
    shell: kubeadm token create --print-join-command
    register: join_command_raw

  - name: set join command
    set_fact:
      join_command: "{{ join_command_raw.stdout_lines[0] }}"

- hosts: workers
  become: yes
  tasks:
  - name: join cluster
    shell: "{{ hostvars['master'].join_command }} >> node_joined.txt"
    args:
      chdir: $HOME
      creates: node_joined.txt
```


### Run the above ansilbe playbook, with the host folder setuped earlier
`ansible-playbook -i hosts ~/kube-cluster/workers.yml` 

### On k8s control node export k8s admin conf. 
### Put following line in /root/.profile

`export KUBECONFIG=/etc/kubernetes/admin.conf`

### Then exit and log back in again

### To verify the Cluster
`kubectl get nodes -o wide` 
