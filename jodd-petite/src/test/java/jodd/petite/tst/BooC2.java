// Copyright (c) 2003-present, Jodd Team (jodd.org). All Rights Reserved.

package jodd.petite.tst;

import jodd.petite.meta.PetiteInject;

public class BooC2 {

	private Foo foo;
	private Zoo zoo;

	@PetiteInject
	private BooC2(Foo foo, Zoo zoo) {
		this.foo = foo;
		this.zoo = zoo;
	}

	public Foo getFoo() {
		return foo;
	}


	public Zoo getZoo() {
		return zoo;
	}
}
