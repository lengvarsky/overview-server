expect = require('chai').expect
browser = require('../lib/browser')
Faker = require('Faker')
wd = require('wd')

Url =
  index: '/admin/users'
  login: '/login'

userToTrXPath = (email) -> "//tr[contains(td[@class='email'], '#{email}')]"

Methods =
  waitForUserLoaded: (email) ->
    @
      .waitForElementByXPath(userToTrXPath(email))

  deleteUser: (email) ->
    @
      .waitForUserLoaded(email)
      .listenForJqueryAjaxComplete()
      .acceptingNextAlert()
      .elementByXPath("#{userToTrXPath(email)}//a[@class='delete']").click()
      .waitForJqueryAjaxComplete()

describe 'UserAdmin', ->
  @timeout(5000)
  adminBrowser = null

  before ->
    wd.addPromiseChainMethod(key, func) for key, func of Methods

    adminBrowser = browser.create()
      .get(Url.index) # log in. XXX make this more generic
      .elementByCss('.session-form [name=email]').type(browser.adminLogin.email)
      .elementByCss('.session-form [name=password]').type(browser.adminLogin.password)
      .elementByCss('.session-form [type=submit]').click()
      .waitForUserLoaded(browser.adminLogin.email)

  after ->
    adminBrowser.quit().done()
    wd.removeMethod(key) for key, __ of Methods

  describe 'index', ->
    describe 'creating a new user', ->
      userEmail = null
      userPassword = 'PhilNettOk7'
      trXPath = null # XPath string like //tr[...], selecting the added user

      beforeEach ->
        userEmail = Faker.Internet.email()
        trXPath = "//tr[contains(td[@class='email'], '#{userEmail}')]"

        adminBrowser
          .elementByCss('.new-user input[name=email]').type(userEmail)
          .elementByCss('.new-user input[name=password]').type(userPassword)
          .elementByCss('.new-user input[type=submit]').click()

      it 'should show the user', ->
        adminBrowser
          .waitForUserLoaded(userEmail)
          .deleteUser(userEmail)

      it 'should delete the user', ->
        adminBrowser
          .waitForUserLoaded(userEmail)
          .deleteUser(userEmail)
          .elementByXPathOrNull(trXPath).should.eventually.be.null
          .get(Url.index) # refresh
          .waitForElementByCss('table.users tbody tr')
          .elementByXPathOrNull("#{trXPath}").should.eventually.be.null

      it 'should promote and demote the user', ->
        adminBrowser
          .waitForUserLoaded(userEmail)
          .elementByXPath("#{trXPath}//td[@class='is-admin']").text().should.eventually.contain('no')
          .listenForJqueryAjaxComplete()
          .elementByXPath("#{trXPath}//a[@class='promote']").click()
          .waitForJqueryAjaxComplete()
          .elementByXPath("#{trXPath}//td[@class='is-admin']").text().should.eventually.contain('yes')
          .get(Url.index) # refresh
          .waitForUserLoaded(userEmail)
          .elementByXPath("#{trXPath}//td[@class='is-admin']").text().should.eventually.contain('yes')
          .listenForJqueryAjaxComplete()
          .elementByXPath("#{trXPath}//a[@class='demote']").click()
          .waitForJqueryAjaxComplete()
          .elementByXPath("#{trXPath}//td[@class='is-admin']").text().should.eventually.contain('no')
          .get(Url.index) # refresh
          .waitForUserLoaded(userEmail)
          .elementByXPath("#{trXPath}//td[@class='is-admin']").text().should.eventually.contain('no')
          .deleteUser(userEmail)

      it 'should create a user who can log in', ->
        userBrowser = browser.create()
          .get(Url.login)
          .elementByCss('.session-form [name=email]').type(userEmail)
          .elementByCss('.session-form [name=password]').type(userPassword)
          .elementByCss('.session-form [type=submit]').click()
          .title().should.become('Your document sets')
          .quit()
          .then ->
            adminBrowser
              .deleteUser(userEmail)