package com.example.mspsupportassistant

import com.example.mspsupportassistant.model.LocalCompany
import com.example.mspsupportassistant.model.LocalUser

object CurrentUserHolder {
    var user: LocalUser? = null
    var company : LocalCompany? = null
}