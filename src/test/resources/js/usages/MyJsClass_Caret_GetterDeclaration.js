class MyJsClass_Caret_GetterDeclaration {
  constructor() {
    this._foo = "later"
  }

  hi() {
  }

  get <caret>Yeah() {
    return this._foo
  }
  set Yeah( value ) {
    this._foo = value
  }
}