package com.boghus.codereview.action

import spock.lang.Specification

class MainSpec extends Specification {
    def 'bootstrap exposes a main entry point'() {
        expect:
        Main.declaredMethods*.name.contains('main')
    }
}
