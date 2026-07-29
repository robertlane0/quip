#!/bin/bash
set -euo pipefail

REAL_USER="${SUDO_USER:-$USER}"

if [ "$REAL_USER" = "root" ]; then
    echo "[Error] Cannot determine the real user. Run with 'sudo ./linux_perms.sh', not as root directly." >&2
    exit 1
fi

RULES_FILE="/etc/udev/rules.d/99-uinput.rules"
RULE_CONTENT='KERNEL=="uinput", MODE="0660", GROUP="input", OPTIONS+="static_node=uinput"'

# Create udev rule (idempotent)
if [ -f "$RULES_FILE" ] && grep -qF "$RULE_CONTENT" "$RULES_FILE"; then
    echo "[OK] udev rule already present in $RULES_FILE"
else
    echo "$RULE_CONTENT" | sudo tee "$RULES_FILE" > /dev/null
    echo "[OK] Created udev rule: $RULES_FILE"
fi

# Add user to input group (idempotent)
if id -nG "$REAL_USER" | grep -qw input; then
    echo "[OK] User '$REAL_USER' is already in the 'input' group."
else
    sudo usermod -aG input "$REAL_USER"
    echo "[OK] Added '$REAL_USER' to the 'input' group. Log out and back in for this to take effect."
fi

sudo udevadm control --reload-rules && sudo udevadm trigger
echo "[OK] udev rules reloaded."
