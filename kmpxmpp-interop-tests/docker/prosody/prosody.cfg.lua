admins = { "admin@localhost" }

modules_enabled = {
  "roster";
  "saslauth";
  "dialback";
  "disco";
  "private";
  "version";
  "uptime";
  "time";
  "ping";
  "register";
  "admin_adhoc";
}

modules_disabled = {
}

allow_registration = false
authentication = "internal_hashed"

c2s_require_encryption = false
allow_unencrypted_plain_auth = true

pidfile = "/var/run/prosody/prosody.pid"

VirtualHost "localhost"
