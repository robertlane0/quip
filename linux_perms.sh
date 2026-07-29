#!/bin/bash

echo 'KERNEL=="uinput", MODE="0660", GROUP="input", OPTIONS+="static_node=uinput"' | sudo tee /etc/udev/rules.d/99-uinput.rules

sudo usermod -aG input $USER

sudo udevadm control --reload-rules && sudo udevadm trigger
