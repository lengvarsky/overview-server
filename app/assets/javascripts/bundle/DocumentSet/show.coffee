requirejs.config({
  baseUrl: '/assets/javascripts'

  #enforceDefine: true

  shim: {
    'backbone': {
      deps: [ 'jquery', 'underscore' ]
      exports: 'Backbone'
    }
    'base64': { exports: 'Base64' }
    'bootstrap-alert': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.alert'
    }
    'bootstrap-collapse': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.collapse'
    }
    'bootstrap-dropdown': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.dropdown'
    }
    'bootstrap-modal': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.modal'
    }
    'bootstrap-popover':
      deps: [ 'jquery', 'bootstrap-tooltip' ]
      exports: 'jQuery.fn.popover'
    'bootstrap-tab': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.tab'
    }
    'bootstrap-tooltip':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.tooltip'
    'bootstrap-transition': {
      deps: [ 'jquery' ]
    }
    spectrum: {
      deps: [ 'jquery', 'tinycolor' ]
      exports: 'jQuery.fn.spectrum'
    }
    underscore: { exports: '_' }
    tinycolor: { exports: 'tinycolor' }
    md5: { exports: 'CryptoJS.MD5' }
  }

  paths: {
    'backbone': 'vendor/backbone'
    'base64': 'vendor/base64'
    'bootstrap-alert': 'vendor/bootstrap-alert'
    'bootstrap-collapse': 'vendor/bootstrap-collapse'
    'bootstrap-dropdown': 'vendor/bootstrap-dropdown'
    'bootstrap-modal': 'vendor/bootstrap-modal'
    'bootstrap-popover': 'vendor/bootstrap-popover'
    'bootstrap-tab': 'vendor/bootstrap-tab'
    'bootstrap-tooltip': 'vendor/bootstrap-tooltip'
    'bootstrap-transition': 'vendor/bootstrap-transition'
    jquery: 'vendor/jquery-2-1-0'
    'jquery.mousewheel': 'vendor/jquery.mousewheel'
    'jquery.validate': 'vendor/jquery.validate'
    md5: 'vendor/md5'
    rsvp: 'vendor/rsvp'
    spectrum: 'vendor/spectrum'
    tinycolor: 'vendor/tinycolor'
    underscore: 'vendor/underscore'
  }
})

require [
  'for-view/DocumentSet/show'
], ->
