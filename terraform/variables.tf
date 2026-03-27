variable "compartment_ocid" {
  description = "OCI Compartment OCID"
  type        = string
}

variable "availability_domain" {
  description = "Availability Domain name (e.g. 'Xyzz:EU-FRANKFURT-1-AD-1')"
  type        = string
}

variable "ssh_public_key" {
  description = "SSH public key for opc user"
  type        = string
}

variable "region" {
  description = "OCI region"
  type        = string
  default     = "eu-frankfurt-1"
}
