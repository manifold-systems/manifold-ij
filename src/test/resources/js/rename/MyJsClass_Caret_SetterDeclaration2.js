class MyJsClass_Caret_SetterDeclaration {
  constructor() {
    this._foo = "later"
  }

  hi() {
  }

  get yeah() {
    return this._foo
  }
  set yeah2( value ) {
    this._foo = value
  }
}